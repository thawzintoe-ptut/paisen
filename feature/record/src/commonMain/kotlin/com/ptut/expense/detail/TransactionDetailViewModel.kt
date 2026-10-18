package com.ptut.expense.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionSource
import com.ptut.expense.domain.colorName
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.dayAndMonth
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.displayName
import com.ptut.expense.domain.filedAs
import com.ptut.expense.domain.format
import com.ptut.expense.domain.formatCompactParts
import com.ptut.expense.domain.iconName
import com.ptut.expense.domain.isAutomatic
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.ledger.rowWithId
import com.ptut.expense.domain.monthShort
import com.ptut.expense.domain.periodsMovedByDeleting
import com.ptut.expense.domain.phrase
import com.ptut.expense.domain.rules.RememberedMerchant
import com.ptut.expense.domain.rules.RuleRepository
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.sharedExpenseOf
import com.ptut.expense.domain.slip.shownShop
import com.ptut.expense.domain.spoken
import com.ptut.expense.domain.timeOfDayIn
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.MetaGlyph
import com.ptut.expense.ui.ToastAction
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import com.ptut.expense.ui.TransactionMetaRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** A field the record does not carry. Never blank: an empty line reads as a rendering fault. */
private const val NOT_RECORDED = "—"

/**
 * FR-21's record, on its own screen because the action it leads to is destructive.
 *
 * It observes [LedgerRepository.allTransactions] rather than asking for one row by id. That flow
 * is already hot — the Ledger holds it open — so this costs a `firstOrNull` per emission and no
 * new query, and it keeps the screen live: #53 corrects a row and this redraws, where a value
 * read once would keep showing the figure the reader has just changed.
 */
class TransactionDetailViewModel(
    private val transactionId: String,
    private val ledger: LedgerRepository,
    private val toasts: ToastMessages,
    private val categories: List<Category> = Category.seed(),
    /** Read when the screen is built, like [categories]: the record names its sub-category (#112). */
    private val subCategories: List<SubCategory> = emptyList(),
    /**
     * The shops the app has been taught. Read so the record can say which rule filed it and lead
     * to the one screen that may change it — FR-12's correction had nowhere to live once rev 5
     * took the shops screen away.
     *
     * Null where nothing binds it, which is a screen test's graph rather than a device. The record
     * then draws without the row, which is the same thing it does for a row no rule filed.
     */
    private val rules: RuleRepository? = null,
    /** FR-19: the transaction's own Bangkok day and time, never the device's. */
    private val zone: TimeZone = BangkokTime,
    /** Only to ask what today is. Which totals a delete moves depends on the day it happens. */
    private val clock: Clock = Clock.System,
    /** #114: a haptic on every save and delete, played by the shell. */
    private val haptics: Haptics = Haptics(),
    /** #186: v2's groups. Null in a screen test's graph, and the record then draws as v1 does. */
    private val shared: SharedRepository? = null,
) : ViewModel() {

    private val asking = MutableStateFlow(false)

    private val alsoRemove = MutableStateFlow(true)

    private val gone = MutableStateFlow(false)

    /**
     * True once the row is out of the ledger and the undo is on screen. The shell leaves on this
     * rather than on the tap: popping first destroys this holder, and a cancelled `viewModelScope`
     * would take the delete or the undo with it.
     */
    val isDeleted: StateFlow<Boolean> = gone.asStateFlow()

    val uiState: StateFlow<TransactionDetailUiState> =
        // Rules are observed too, so a rule changed elsewhere redraws the card (#117).
        combine(
            ledger.allTransactions,
            asking,
            rules?.rules ?: flowOf(emptyList()),
            shared?.snapshot ?: flowOf(SharedSnapshot.Empty),
            alsoRemove,
        ) { rows, confirming, known, groups, remove ->
            when (val state = detailOf(rows, known)) {
                is TransactionDetailUiState.Loaded -> state.withShared(groups).copy(confirmingDelete = confirming, alsoRemoveFromGroup = remove)
                else -> state
            }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TransactionDetailUiState.Loading,
            )

    /**
     * The seam now says which emptiness it means, so this asks it instead of guessing. It used to
     * read any empty ledger as still-loading to avoid flashing "gone" on a cold open, which cost
     * the opposite mistake: delete the only transaction and the record really was gone, but the
     * screen hung on a blank header saying nothing.
     */
    private fun detailOf(snapshot: LedgerSnapshot, known: List<RememberedMerchant>): TransactionDetailUiState = when (snapshot) {
        LedgerSnapshot.Unanswered -> TransactionDetailUiState.Loading

        is LedgerSnapshot.Rows ->
            snapshot.transactions
                .firstOrNull { it.id == transactionId }
                ?.toLoaded(known)
                ?: TransactionDetailUiState.Missing
    }

    private fun Transaction.toLoaded(known: List<RememberedMerchant>): TransactionDetailUiState.Loaded {
        val merchant = displayName(categories, subCategories)
        val moved = totalsMovedPhrase()
        val text = AppText.copy.record
        return TransactionDetailUiState.Loaded(
            merchant = merchant,
            amount = amount.formatCompactParts(),
            spoken = "$merchant, ${amount.spoken()} ${direction.spokenVerb()}",
            // The card is the only place the merchant's own category glyph appears, which is
            // why the CATEGORY row below carries the plain tag instead.
            categoryIcon = iconName(categories),
            categoryColor = colorName(categories),
            rows = metaRows(),
            consequence = consequence(),
            deleteMoves = moved?.let { text.comesOff(amount.format(), it) } ?: text.comesOffHistory(amount.format()),
            isIncome = direction == Direction.Income,
            ruleFooter = ruleFooter(known),
            shopRule = shopRule(known),
        )
    }

    /**
     * The Why card (p16), only on a row a rule filed and still in that rule's category (#117). A
     * hand entry, a confirmed slip or an edited row has a different story than the card would tell.
     */
    private fun Transaction.shopRule(known: List<RememberedMerchant>): ShopRuleRowUiModel? {
        val shop = counterparty ?: return null
        val rule = known.firstOrNull { it.id == ruleId } ?: return null
        if (rule.categoryId != categoryId) return null

        val lands = categories.firstOrNull { it.id == rule.categoryId }?.name ?: return null
        return ShopRuleRowUiModel(shop = shop, filesUnder = lands, why = rule.why(shop, lands))
    }

    /** "You picked it for Foodland on 2 Aug. 11 slips since went the same way." (p16) */
    private fun RememberedMerchant.why(shop: String, category: String): String {
        val text = AppText.copy.record
        val picked = if (createdAtEpochMillis > 0) {
            val on = createdAtEpochMillis.dateIn(zone)
            text.pickedOn(shop, category, on.day, on.monthShort())
        } else {
            text.picked(shop, category)
        }
        val since = if (filedByRule > 1) text.slipsSince(filedByRule) else text.firstSlip
        return "$picked $since"
    }

    /**
     * NFR-4 deletes the picture once the row is durable, so nothing can be re-read and the reader
     * has to be told before they tap. A row nobody imported never had one, and claiming it did
     * would be a confident wrong answer about the reader's own data.
     */
    private fun Transaction.consequence(): String {
        if (!source.isAutomatic) return totalsMoved()
        return "${AppText.copy.record.slipImageGone} ${totalsMoved()}"
    }

    /**
     * FR-21 wants the damage in baht and the totals that actually move. A row from last week
     * moves the month and not the week, so the periods are computed rather than recited — a
     * fixed sentence would tell the reader a total is changing when it is not.
     */
    private fun Transaction.totalsMoved(): String {
        val text = AppText.copy.record
        return totalsMovedPhrase()?.let { text.takesOff(amount.format(), it) } ?: text.takesOffHistory(amount.format())
    }

    /**
     * "this month", or null when nothing moves. A row too old to touch any of the three still
     * leaves the ledger, and both sentences say so in their own words for that case.
     */
    private fun Transaction.totalsMovedPhrase(): String? {
        val moved = periodsMovedByDeleting(
            occurredAt = occurredAtEpochMillis.dateIn(zone),
            today = clock.now().dateIn(zone),
        )
        return moved.takeIf { it.isNotEmpty() }?.phrase()
    }

    /**
     * FR-12, and required: without it people assume delete undoes the learning as well. A row
     * added by hand taught no rule, so it gets the true version of the same promise rather than
     * one about a merchant it never had.
     */
    private fun Transaction.ruleFooter(known: List<RememberedMerchant>): String {
        val text = AppText.copy.record
        val merchant = counterparty ?: return text.handFooter
        val key = merchant.trim().lowercase()
        val rule = known.firstOrNull { it.counterparty.trim().lowercase() == key }
        val sortedInto = rule?.let { held -> categories.firstOrNull { it.id == held.categoryId }?.name }
            ?: return text.onlyThisOne
        return text.staysSorted(shownShop(merchant, AppText.copy.slips.unknownShop), sortedInto)
    }

    private fun Transaction.metaRows(): List<TransactionMetaRow> = listOfNotNull(
        TransactionMetaRow("CATEGORY", filedAs(categories, subCategories), icon = MetaGlyph.Tag),
        // In full here: a row's name is only the note's first line, cut to fit (#210).
        memo?.let { TransactionMetaRow("NOTE", it, icon = MetaGlyph.Note) },
        TransactionMetaRow("DATE", occurredAt(), icon = MetaGlyph.Time),
        TransactionMetaRow("SOURCE", provenance(), icon = MetaGlyph.Source),
        // The handoff asks for the reference in monospace "as on confirm slip". #20 moved
        // tabular figures onto every type slot, so confirm slip no longer marks it either and a
        // per-field flag here would be a switch that changes nothing.
        TransactionMetaRow("REFERENCE", reference ?: NOT_RECORDED, icon = MetaGlyph.Tag),
    )

    private fun Transaction.occurredAt(): String =
        AppText.copy.phrases.dateAndTime(occurredAtEpochMillis.dateIn(zone).dayLabel(), occurredAtEpochMillis.timeOfDayIn(zone))

    /**
     * Where the row came from, and when it was last corrected. Two facts, so two clauses —
     * which is why [Transaction.editedAt] is a field of its own rather than a fourth source.
     *
     * The handoff writes this as "K PLUS slip · filed by rule". The rule half is left off: a row
     * carries no rule id, so this cannot tell a slip a rule filed from one the reader categorised
     * in the review queue — and a confident wrong answer on the screen whose next action is
     * delete costs more than saying less.
     */
    private fun Transaction.provenance(): String {
        val origin = origin()
        val corrected = editedAt ?: return origin
        return "$origin · ${AppText.copy.record.editedOn(corrected.dateIn(zone).dayAndMonth())}"
    }

    private fun Transaction.origin(): String = when (source) {
        // "K PLUS slip · Screenshots" once the folder is stored (#170).
        TransactionSource.Scan -> listOfNotNull(AppText.copy.record.kplusSlip, sourceFolder).joinToString(" · ")

        TransactionSource.Share -> AppText.copy.record.sharedSlip

        TransactionSource.Manual -> AppText.copy.record.addedByHand
    }

    /** #186: which group holds this purchase, or which groups it could go to. */
    private fun TransactionDetailUiState.Loaded.withShared(snapshot: SharedSnapshot): TransactionDetailUiState.Loaded {
        if (shared == null) return this
        val group = snapshot.sharedExpenseOf(transactionId)?.let { snapshot.group(it.groupId) }
        return copy(
            canShare = group == null && !isIncome,
            shareGroups = snapshot.groups.map { ShareGroupUiModel(it.id, it.name, AppText.copy.record.people(it.activeMembers.size)) },
            sharedGroupId = group?.id,
            sharedGroupName = group?.name,
        )
    }

    fun setAlsoRemove(on: Boolean) {
        alsoRemove.value = on
    }

    /** FR-21: the tap asks, it does not delete. Nothing reaches the ledger until [confirmDelete]. */
    fun askToDelete() {
        asking.value = true
    }

    /** The calm option. Named for its outcome, the way the button the reader taps is. */
    fun keepIt() {
        asking.value = false
    }

    /**
     * Deletes now and offers the row back, rather than marking it and sweeping later.
     *
     * The alternative — a soft delete finalised when the window closes — would put a `deletedAt`
     * filter on every query in the app to buy six seconds, and both behave the same when the
     * process dies mid-window: the delete stands. Here the only thing at risk is the undo, which
     * is the thing the reader can already see is temporary.
     */
    fun confirmDelete() {
        val row = ledger.allTransactions.value.rowWithId(transactionId) ?: return
        val name = row.displayName(categories, subCategories)
        asking.value = false
        val repository = shared
        val expense = repository?.snapshot?.value?.sharedExpenseOf(row.id)
        val group = expense?.let { repository.snapshot.value.group(it.groupId) }
        // #186: ticked removes the group's copy too; unticked keeps it and only the link goes.
        val removing = expense?.takeIf { alsoRemove.value }
        val removeShared = removing != null
        val text = AppText.copy.record
        viewModelScope.launch {
            ledger.delete(row.id)
            if (removing != null) repository.removeExpense(removing.id)
            haptics.deleted()
            toasts.show(
                ToastMessage(
                    text = if (removeShared && group != null) text.deletedFromGroup(name, group.name) else text.deleted(name),
                    tone = ToastTone.Deleted,
                    // The action outlives this holder: the screen pops as soon as the row goes,
                    // so the shell runs it and the row travels in the lambda rather than in here.
                    action = ToastAction(AppText.copy.common.undo) {
                        ledger.restore(row)
                        if (expense != null) {
                            if (removeShared) repository.restoreExpense(expense.id)
                            repository.relink(expense.id, row.id)
                        }
                    },
                ),
            )
            gone.value = true
        }
    }
}

/** TalkBack reads "−" as "dash", so the direction is spoken as a word beside the figure. */
private fun Direction.spokenVerb(): String = when (this) {
    Direction.Income -> AppText.copy.record.received
    Direction.Expense -> AppText.copy.record.spent
}
