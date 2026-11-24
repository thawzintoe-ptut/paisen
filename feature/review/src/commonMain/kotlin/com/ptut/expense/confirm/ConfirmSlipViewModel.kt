package com.ptut.expense.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ForeignAmount
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Separators
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.dayAndMonth
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.format
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.ledger.GetLastRateUseCase
import com.ptut.expense.domain.record.priceForeignAmount
import com.ptut.expense.domain.rules.ShopNames
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.SlipDebt
import com.ptut.expense.domain.shared.rateUnitLabel
import com.ptut.expense.domain.slip.ConfirmSlipUseCase
import com.ptut.expense.domain.slip.DiscardSlipUseCase
import com.ptut.expense.domain.slip.DiscardSlipWithUndoUseCase
import com.ptut.expense.domain.slip.FindSlipDebtsUseCase
import com.ptut.expense.domain.slip.GetQueuedSlipUseCase
import com.ptut.expense.domain.slip.ObserveSlipInQueueUseCase
import com.ptut.expense.domain.slip.PaySlipDebtUseCase
import com.ptut.expense.domain.slip.SlipConfirmation
import com.ptut.expense.domain.slip.isReaderNoise
import com.ptut.expense.domain.slip.shownShop
import com.ptut.expense.domain.under
import com.ptut.expense.domain.usage.UsageEvent
import com.ptut.expense.domain.usage.UsageFlag
import com.ptut.expense.domain.usage.UsageLog
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.currencyCopy
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The confirm screen's state holder: type the amount against the slip, pick a category once, and
 * the merchant is remembered.
 *
 * It watches the slip in the queue the ledger already exposes rather than querying for one, so
 * a slip discarded from another screen says it has gone instead of staying stale (#217).
 */
class ConfirmSlipViewModel(
    private val slipId: String,
    getSlip: GetQueuedSlipUseCase,
    observeSlip: ObserveSlipInQueueUseCase,
    private val toasts: ToastMessages,
    // #26: every category; the slip's own direction picks which are offered, so money in offers income.
    private val categories: List<Category> = Category.seed(),
    private val subCategories: List<SubCategory> = emptyList(),
    /** Rows filed per category id, counted once for the app; none offers the categories in their own order. */
    private val categoryUsage: Flow<Map<String, Int>> = flowOf(emptyMap()),
    /** The category a shop rule or a known shop name points at, or null to leave the pick to the reader. */
    private val suggestCategory: (shop: String) -> Category? = { null },
    /** The shared-group debts a slip could have paid. None in a build with no shared groups. */
    private val findDebts: FindSlipDebtsUseCase = FindSlipDebtsUseCase(),
    private val clock: Clock = Clock.System,
    /** #22: debug builds only until the weekly note has been tried. */
    private val offersPlanned: Boolean = false,
    /** #114: a haptic on every save and delete, played by the shell. */
    private val haptics: Haptics = Haptics(),
    /** The rate a foreign slip's currency was last saved at, so only the first slip in it asks. */
    private val getLastRate: GetLastRateUseCase = GetLastRateUseCase(),
    private val confirmSlip: ConfirmSlipUseCase,
    /** Records a group debt the slip paid. Does nothing in a build with no shared groups. */
    private val paySlipDebt: PaySlipDebtUseCase = PaySlipDebtUseCase(),
    private val discardSlip: DiscardSlipUseCase,
    /** Not mine: the slip goes once its Undo has left the screen (#219). */
    private val discardWithUndo: DiscardSlipWithUndoUseCase,
    /** #115: a count per slip confirmed, and whether its amount or shop was changed. */
    private val usageLog: UsageLog = UsageLog.None,
) : ViewModel() {

    private var usage: Map<String, Int> = emptyMap()

    private var slip: QueuedSlip? = getSlip(slipId)

    private var debts: List<SlipDebt> = slip?.let(findDebts::invoke).orEmpty()
    private var chosenDebt = 0

    private val state = MutableStateFlow(
        slip.toUiState().copy(phase = if (slip == null) ConfirmSlipPhase.Loading else ConfirmSlipPhase.Ready, debtMatch = debtMatch()),
    )
    val uiState: StateFlow<ConfirmSlipUiState> = state.asStateFlow()

    init {
        slip?.takeIf { it.isForeign }?.let { found -> viewModelScope.launch { loadRate(found.currency) } }
        viewModelScope.launch {
            observeSlip(slipId).collectLatest { found -> if (found != null) arrive(found) else leave() }
        }
        // A cold start can open this before the ledger answered, so the chips reorder when it does.
        viewModelScope.launch {
            categoryUsage.collect { counted ->
                usage = counted
                state.update { it.copy(categoryUsage = counted) }
            }
        }
    }

    /** The first sight fills the form. A later one keeps what the reader has typed. */
    private fun arrive(found: QueuedSlip) {
        val first = slip == null
        slip = found
        if (first) {
            debts = findDebts(found)
            // 21b: a category picked while the slip was read is the reader's, so it stays.
            val picked = state.value.category
            val read = found.toUiState().copy(debtMatch = debtMatch())
            state.value = if (picked == null) read else read.copy(category = picked, subCategories = subCategories.under(picked.id), suggestedFromShop = false)
            if (found.isForeign) viewModelScope.launch { loadRate(found.currency) }
        } else {
            // A later sight never undoes a save in flight or one already done.
            state.update { if (it.done) it else it.copy(phase = ConfirmSlipPhase.Ready) }
        }
    }

    /** Gone by this screen's own Confirm or Not mine is expected. Gone any other way is said. */
    private suspend fun leave() {
        // Before Room answers the queue is empty too, so a slip not seen yet gets a moment first.
        if (slip == null) delay(MISSING_GRACE)
        state.update { if (it.done) it else it.copy(phase = ConfirmSlipPhase.Missing) }
    }

    fun type(entry: AmountEntry) = state.update { it.copy(entry = entry).withHome() }

    /** #33: home money for one rate unit of the slip's currency, typed. */
    fun typeRate(text: String) = state.update { it.copy(rateText = text, askForRate = false).withHome() }

    /** The last rate saved opens the field, so only the first slip in that money has to ask. */
    private suspend fun loadRate(currency: String) {
        val last = getLastRate(currency) ?: return
        state.update { if (it.rateText.isEmpty()) it.copy(rateText = last.typedFor(Currencies.of(currency).rateUnit)).withHome() else it }
    }

    /** What the typed amount at the typed rate saves as, or null while either is not a number. */
    private fun ConfirmSlipUiState.withHome(): ConfirmSlipUiState = copy(homeLine = foreignOf(this)?.let { currencyCopy().savedAs(it.inHome.format()) })

    private fun foreignOf(current: ConfirmSlipUiState): ForeignAmount? {
        val slip = slip ?: return null
        return priceForeignAmount(Currencies.of(slip.currency), current.entry.typed, current.rateText)
    }

    /** #22: held until Save, like the category. */
    fun markPlanned(on: Boolean) = state.update { it.copy(planned = on) }

    /** The reader's name for this shop; it goes onto the rule this save creates. */
    fun nameShop(name: String) = state.update { it.copy(shopName = name) }

    /**
     * Tapping the chosen chip again clears it, so a mis-tap is undone where it was made. Any pick
     * clears the sub-category: it belongs to one parent (p34).
     */
    fun choose(category: Category) = state.update {
        val chosen = if (it.category?.id == category.id) null else category
        it.copy(
            category = chosen,
            subCategories = subCategories.under(chosen?.id),
            subCategory = null,
            askForCategory = it.askForCategory && chosen == null,
            suggestedFromShop = false,
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

        // #33: a foreign slip saves its home amount at the typed rate, and keeps the original and the rate beside it.
        val foreign = foreignOf(current)
        if (slip.isForeign && foreign == null) {
            state.update { it.copy(askForRate = true) }
            return
        }
        val amount = foreign?.inHome ?: current.entry.toSatang()
        val typed = current.shopName.trim()
        val confirmation = SlipConfirmation(
            slip = slip,
            categoryId = category.id,
            subCategoryId = current.subCategory?.id,
            amount = amount,
            foreign = foreign,
            typedShop = typed,
            shopRead = current.shopRead,
            planned = current.planned,
        )
        state.update { it.copy(phase = ConfirmSlipPhase.Saving) }
        viewModelScope.launch {
            val filed = confirmSlip(confirmation)
            if (filed.isFailure) {
                // #76's shape: Confirm unlocks and the typed form stays, so a second tap can try again.
                toasts.show(ToastMessage(AppText.copy.slips.slipNotSaved, tone = ToastTone.Error))
                state.update { it.copy(phase = ConfirmSlipPhase.Ready) }
                return@launch
            }
            haptics.saved()
            val edited = (slip.amount != null && slip.amount != amount) || (typed.isNotEmpty() && typed != slip.merchant)
            usageLog.log(UsageEvent.SlipChecked, mapOf(UsageFlag.Edited to edited))
            usageLog.log(UsageEvent.SlipFiled, mapOf(UsageFlag.Checked to true))
            val shown = typed.ifEmpty { null } ?: filed.getOrNull()?.counterparty?.let { shownShop(it, AppText.copy.slips.unknownShop) }
            val saved = AppText.copy.slips.saved(amount.formatCompact(), slip.occurredAtEpochMillis.filedDay(), category.name, shown)
            toasts.show(ToastMessage(saved))
            state.update { it.copy(phase = ConfirmSlipPhase.Saved) }
        }
    }

    /**
     * NOT MINE (#161), which is today's discard (#147, R4): the row and the picture go and no hash
     * is kept, so a photo taken again of the same slip is read again (FR-8). They go when the Undo
     * window closes (#219), so the screen leaves at once and Undo brings the slip back to To check.
     */
    fun notMine() {
        if (state.value.saving) return
        state.update { it.copy(phase = ConfirmSlipPhase.Saving) }
        viewModelScope.launch {
            // By the id this screen was opened for, so a slip still being read can go too (21b).
            discardWithUndo(slipId)
            haptics.deleted()
            state.update { it.copy(phase = ConfirmSlipPhase.Dismissed) }
        }
    }

    fun chooseDebt(index: Int) {
        if (index !in debts.indices) return
        chosenDebt = index
        state.update { it.copy(debtMatch = debtMatch()) }
    }

    fun notThisDebt() {
        debts = emptyList()
        state.update { it.copy(debtMatch = null) }
    }

    /**
     * Records the picked debt as paid by bank transfer at the slip's own time. The person chose
     * whether the slip also stays as their own spending: kept, it is filed as usual below; not
     * kept, it leaves To check, since paying a friend back is not new spending.
     */
    fun payDebt(keepInSpending: Boolean) {
        val slip = slip ?: return
        val debt = debts.getOrNull(chosenDebt) ?: return
        if (state.value.saving) return
        state.update { it.copy(phase = ConfirmSlipPhase.Saving) }
        viewModelScope.launch {
            if (paySlipDebt(slip, debt).isFailure) {
                // As save: the debt card stays and unlocks, so a second tap can try again.
                toasts.show(ToastMessage(AppText.copy.shared.payment.didntSave(debt.amount.formatCompact()), tone = ToastTone.Error))
                state.update { it.copy(phase = ConfirmSlipPhase.Ready) }
                return@launch
            }
            debts = emptyList()
            val text = AppText.copy.slips
            haptics.saved()
            toasts.show(ToastMessage(text.debtRecorded(debt.amount.format(), debt.toName, debt.groupName)))
            if (keepInSpending) {
                state.update { it.copy(phase = ConfirmSlipPhase.Ready, debtMatch = null, debtPaidNote = text.debtKeepNote(debt.toName)) }
            } else {
                // Dismissed first, so the queue emptying reads as this screen's own doing.
                state.update { it.copy(phase = ConfirmSlipPhase.Dismissed, debtMatch = null) }
                discardSlip(slip.id)
            }
        }
    }

    private fun debtMatch(): DebtMatchUiModel? {
        val chosen = debts.getOrNull(chosenDebt) ?: return null
        val text = AppText.copy.slips
        return DebtMatchUiModel(
            question = text.debtQuestion(chosen.amount.format(), chosen.toName, chosen.groupName),
            choices = debts.map { text.debtChoice(it.toName, it.groupName) },
            chosen = chosenDebt,
        )
    }

    private fun QueuedSlip?.toUiState(): ConfirmSlipUiState {
        val offered = categories.forDirection(this?.direction ?: Direction.Expense)
        val suggested = this?.merchant?.takeUnless { it.isReaderNoise() }?.let(suggestCategory)?.takeIf { it in offered }
        return ConfirmSlipUiState(
            // Saving still hands the shop rule `slip.merchant`, noise and all; only the screen says "Unknown shop".
            merchant = this?.merchant?.let { shownShop(it, AppText.copy.slips.unknownShop) }.orEmpty(),
            date = this?.occurredAtEpochMillis?.slipDate().orEmpty(),
            reference = this?.note.orEmpty(),
            // An amount the parser lifted off the slip is what the reader is here to check. One it
            // could not read arrives null, and the pad stays empty rather than offering a figure.
            entry = this?.amount?.let { if (isForeign) AmountEntry.of(it, Currencies.of(currency).digits) else AmountEntry.of(it) } ?: AmountEntry(),
            currencySymbol = this?.let { Currencies.of(it.currency).symbol } ?: Home.money.symbol,
            asksRate = this?.isForeign == true,
            rateUnit = this?.let { Currencies.of(it.currency).rateUnitLabel() }.orEmpty(),
            lowConfidence = this?.lowConfidence.orEmpty(),
            amountUnread = this != null && amount == null,
            category = suggested,
            categories = offered,
            categoryUsage = usage,
            suggestedFromShop = suggested != null,
            subCategories = subCategories.under(suggested?.id),
            moneyIn = this?.direction == Direction.Income,
            offersPlanned = offersPlanned && this?.direction != Direction.Income,
            shopRead = this?.reason != ReviewReason.Unreadable,
            nameable = this?.merchant?.isReaderNoise() == true,
            shopName = this?.merchant?.takeUnless { it.isReaderNoise() }?.let { ShopNames.of(it) ?: it }.orEmpty(),
            slipId = this?.id.orEmpty(),
            pictureCaption = this?.let {
                listOfNotNull(it.sourceFolder, it.occurredAtEpochMillis.dateIn(HomeTime).dayAndMonth()).joinToString(Separators.DOT)
            }.orEmpty(),
        )
    }

    /** FR-19: the slip's own Bangkok day and time, never the device's: "Fri 14 Aug, 18:30" (p13). */
    private fun Long.slipDate(): String {
        val at = Instant.fromEpochMilliseconds(this).toLocalDateTime(HomeTime)
        return AppText.copy.slips.slipWhen(at.date.dayLabel(), "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}")
    }

    /** Today lists only today (FR-16), so a slip from another day says where it went. */
    private fun Long.filedDay(): String {
        val day = dateIn(HomeTime)
        return if (day == clock.now().dateIn(HomeTime)) AppText.copy.slips.savedToday else day.dayLabel()
    }

    private val QueuedSlip.isForeign: Boolean get() = currency != Home.money.code

    private companion object {
        /** Long enough for a cold Room start, and not final: a slip that turns up after it shows. */
        val MISSING_GRACE = 3.seconds
    }
}
