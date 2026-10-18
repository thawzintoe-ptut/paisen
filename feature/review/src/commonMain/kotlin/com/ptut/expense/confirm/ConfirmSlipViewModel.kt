package com.ptut.expense.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.dayAndMonth
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.rules.ShopNames
import com.ptut.expense.domain.slip.ConfirmedSlip
import com.ptut.expense.domain.slip.isReaderNoise
import com.ptut.expense.domain.slip.shownShop
import com.ptut.expense.domain.under
import com.ptut.expense.importer.SlipDiscards
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The confirm screen's state holder: type the amount against the slip, pick a category once, and
 * the merchant is remembered.
 *
 * It watches the slip in the queue the ledger already exposes rather than querying for one, so
 * a slip discarded from another screen says it has gone instead of staying stale (#217).
 */
@OptIn(ExperimentalUuidApi::class)
class ConfirmSlipViewModel(
    slipId: String,
    ledger: LedgerRepository,
    private val importer: SlipImporter,
    private val toasts: ToastMessages,
    private val discards: SlipDiscards,
    // C-2: a slip is money sent, so Income is never on offer here.
    // C-2: a slip is money sent, so only the categories the reader files spending under.
    private val categories: List<Category> = Category.seed().forDirection(Direction.Expense),
    private val subCategories: List<SubCategory> = emptyList(),
    private val clock: Clock = Clock.System,
    /** Injected so a test can name the rows it wrote. Random everywhere else. */
    /** #114: a haptic on every save and delete, played by the shell. */
    private val haptics: Haptics = Haptics(),
    private val newId: () -> String = { Uuid.random().toString() },
) : ViewModel() {

    private var slip: QueuedSlip? = ledger.queuedSlips.value.firstOrNull { it.id == slipId }

    private val state = MutableStateFlow(
        slip.toUiState().copy(phase = if (slip == null) ConfirmSlipPhase.Loading else ConfirmSlipPhase.Ready),
    )
    val uiState: StateFlow<ConfirmSlipUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            ledger.queuedSlips
                .map { queue -> queue.firstOrNull { it.id == slipId } }
                .distinctUntilChanged()
                .collectLatest { found -> if (found != null) arrive(found) else leave() }
        }
    }

    /** The first sight fills the form. A later one keeps what the reader has typed. */
    private fun arrive(found: QueuedSlip) {
        val first = slip == null
        slip = found
        if (first) state.value = found.toUiState() else state.update { it.copy(phase = ConfirmSlipPhase.Ready) }
    }

    /** Gone by this screen's own Confirm or Not mine is expected. Gone any other way is said. */
    private suspend fun leave() {
        // Before Room answers the queue is empty too, so a slip not seen yet gets a moment first.
        if (slip == null) delay(MISSING_GRACE)
        state.update { if (it.saving || it.saved || it.dismissed) it else it.copy(phase = ConfirmSlipPhase.Missing) }
    }

    fun type(entry: AmountEntry) = state.update { it.copy(entry = entry) }

    /** The reader's name for this shop; it goes onto the rule this save creates. */
    fun nameShop(name: String) = state.update { it.copy(shopName = name) }

    /**
     * Tapping the chosen chip again clears it, so a mis-tap is undone where it was made. Any pick
     * clears the sub-category: it belongs to one parent (p34).
     */
    fun choose(category: Category) = state.update {
        val chosen = if (it.category == category) null else category
        it.copy(
            category = chosen,
            subCategories = subCategories.under(chosen?.id),
            subCategory = null,
            askForCategory = it.askForCategory && chosen == null,
        )
    }

    /** Optional, so a second tap takes it off again. The shop rule never learns it (R5 in #147). */
    fun chooseSubCategory(subCategory: SubCategory) = state.update {
        it.copy(subCategory = if (it.subCategory == subCategory) null else subCategory)
    }

    /**
     * FR-11 and FR-13. Refuses out loud, in a line beside Confirm, rather than silently: the add
     * sheet falls back to `Other` when nothing is chosen, and a slip filed under a category nobody
     * picked is a rule nobody agreed to, which then files every later slip from that merchant the
     * same wrong way.
     *
     * [ConfirmSlipUiState.saving] is set before the launch, not inside it, so a second tap in the
     * same frame finds it already true rather than writing the row twice.
     */
    fun save() {
        val current = state.value
        val slip = slip ?: return
        if (current.entry.isEmpty || current.saving) return

        val category = current.category
        if (category == null) {
            state.update { it.copy(askForCategory = true) }
            return
        }

        val amount = current.entry.toSatang()
        val typed = current.shopName.trim()
        // A read shop stays the rule's key, so its next slip still matches; a name typed over it is
        // what rows show. A slip that read no shop takes the typed name as its shop.
        val shop = if (current.shopRead) slip.merchant else typed.ifEmpty { null }
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            importer.confirm(
                ConfirmedSlip(
                    transactionId = newId(),
                    ruleId = newId(),
                    queuedSlipId = slip.id,
                    counterparty = shop,
                    categoryId = category.id,
                    subCategoryId = current.subCategory?.id,
                    amount = amount,
                    occurredAtEpochMillis = slip.occurredAtEpochMillis,
                    reference = slip.note.takeIf { it.isNotBlank() },
                    confirmedAtEpochMillis = clock.now().toEpochMilliseconds(),
                    // What the parser read, not what the reader typed over it. FR-7 kept this
                    // null when the figure did not read, and null is what screen 11c wants too.
                    readAmount = slip.amount,
                    folder = slip.sourceFolder,
                    displayName = typed.takeIf { current.shopRead && it.isNotEmpty() && it != slip.merchant },
                ),
            )
            haptics.saved()
            val shown = typed.ifEmpty { null } ?: shop?.let { shownShop(it, AppText.copy.slips.unknownShop) }
            val saved = AppText.copy.slips.saved(amount.formatCompact(), slip.occurredAtEpochMillis.filedDay(), category.name, shown)
            toasts.show(ToastMessage(saved))
            state.update { it.copy(saving = false, saved = true) }
        }
    }

    /**
     * NOT MINE (#161), which is today's discard (#147, R4): the row and the picture go and no hash
     * is kept, so a photo taken again of the same slip is read again (FR-8). They go when the Undo
     * window closes (#219), so the screen leaves at once and Undo brings the slip back to To check.
     */
    fun notMine() {
        val slip = slip ?: return
        if (state.value.saving) return
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            discards.discard(slip.id)
            haptics.deleted()
            state.update { it.copy(saving = false, dismissed = true) }
        }
    }

    private fun QueuedSlip?.toUiState() = ConfirmSlipUiState(
        // Saving still hands the shop rule `slip.merchant`, noise and all; only the screen says "Unknown shop".
        merchant = this?.merchant?.let { shownShop(it, AppText.copy.slips.unknownShop) }.orEmpty(),
        date = this?.occurredAtEpochMillis?.slipDate().orEmpty(),
        reference = this?.note.orEmpty(),
        // An amount the parser lifted off the slip is what the reader is here to check. One it
        // could not read arrives null, and the pad stays empty rather than offering a figure.
        entry = this?.amount?.let(AmountEntry::of) ?: AmountEntry(),
        lowConfidence = this?.lowConfidence.orEmpty(),
        amountUnread = this != null && amount == null,
        category = null,
        categories = categories,
        saving = false,
        saved = false,
        shopRead = this?.reason != ReviewReason.Unreadable,
        nameable = this?.merchant?.isReaderNoise() == true,
        shopName = this?.merchant?.takeUnless { it.isReaderNoise() }?.let { ShopNames.of(it) ?: it }.orEmpty(),
        slipId = this?.id.orEmpty(),
        pictureCaption = this?.let {
            listOfNotNull(it.sourceFolder, it.occurredAtEpochMillis.dateIn(BangkokTime).dayAndMonth()).joinToString(" · ")
        }.orEmpty(),
    )

    /** FR-19: the slip's own Bangkok day and time, never the device's: "Fri 14 Aug, 18:30" (p13). */
    private fun Long.slipDate(): String {
        val at = Instant.fromEpochMilliseconds(this).toLocalDateTime(BangkokTime)
        return AppText.copy.slips.slipWhen(at.date.dayLabel(), "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}")
    }

    /** Today lists only today (FR-16), so a slip from another day says where it went. */
    private fun Long.filedDay(): String {
        val day = Instant.fromEpochMilliseconds(this).toLocalDateTime(BangkokTime).date
        return if (day == clock.now().toLocalDateTime(BangkokTime).date) AppText.copy.slips.savedToday else day.dayLabel()
    }

    private companion object {
        /** Long enough for a cold Room start, and not final: a slip that turns up after it shows. */
        val MISSING_GRACE = 3.seconds
    }
}
