package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Separators
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.ExpenseCause
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.SharedEvent
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.effectFor
import com.ptut.expense.domain.shared.events
import com.ptut.expense.domain.shared.formatMinor
import com.ptut.expense.domain.shared.moneyFormat
import com.ptut.expense.domain.timeOfDayIn
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/** p32's filters. */
enum class ActivityFilter { All, Expenses, Payments, Edits }

enum class SharedEntryKind { Added, Edited, Removed, Payment }

data class SharedEntryUiModel(
    val id: String,
    val kind: SharedEntryKind,
    val icon: String,
    val title: String,
    /** For an edit: the old value, struck through, then the new one ("$220.00 → $240.00"). */
    val before: String?,
    val after: String?,
    val line: String,
    val tone: BalanceTone,
    /** #64: what a tap opens. Null for a removal, whose row already says what it was. */
    val expenseId: String? = null,
    val paymentId: String? = null,
)

/** [date] keys the day in a list: [label] has no year, so it repeats a year apart. */
data class SharedActivityDay(val date: LocalDate, val label: String, val entries: List<SharedEntryUiModel>)

/** [fresh] is #64's "New since you last looked": others' changes after the mark, kept out of [days]. */
data class SharedActivityUiState(
    val title: String,
    val filter: ActivityFilter,
    val days: List<SharedActivityDay>,
    val fresh: List<SharedEntryUiModel> = emptyList(),
)

/**
 * #140: one timeline of everything shared, newest first. Edits keep the old value beside the new
 * one, so a changed total can always be explained, and every entry says who added it.
 */
class SharedActivityViewModel(
    private val groupId: String?,
    repository: SharedRepository,
    private val clock: Clock = Clock.System,
    /** Where the snapshot is mapped; tests hand in their own. */
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val filter = MutableStateFlow(ActivityFilter.All)
    private val seenMark = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<SharedActivityUiState> = combine(repository.snapshot, filter, seenMark) { snapshot, filter, since ->
        val today = clock.now().dateIn(HomeTime)
        val text = AppText.copy.shared
        val entries = snapshot.events(groupId)
            .filter { event ->
                when (filter) {
                    ActivityFilter.All -> true
                    ActivityFilter.Expenses -> event is SharedEvent.ExpenseAdded || event is SharedEvent.ExpenseRemoved
                    ActivityFilter.Payments -> event is SharedEvent.PaymentMade || event is SharedEvent.PaymentRemovalChanged
                    ActivityFilter.Edits -> event is SharedEvent.ExpenseEdited
                }
            }
            .mapNotNull { event -> snapshot.group(event.groupId)?.let { event to it } }
        val (fresh, earlier) = entries.partition { (event, group) -> group.isNewToMe(event.authorMemberId, event.atEpochMillis, since) }
        SharedActivityUiState(
            title = groupId?.let { snapshot.group(it)?.name?.let(text.log.groupTitle) } ?: text.activity,
            filter = filter,
            fresh = fresh.map { (event, group) -> event.entry(group) },
            days = earlier
                .groupBy { (event, _) -> event.atEpochMillis.dateIn(HomeTime) }
                .map { (date, rows) -> SharedActivityDay(date, dayHeading(date, today), rows.map { (event, group) -> event.entry(group) }) },
        )
    }.flowOn(work).stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), SharedActivityUiState(AppText.copy.shared.activity, ActivityFilter.All, emptyList()))

    fun show(filter: ActivityFilter) {
        this.filter.value = filter
    }

    /** #64: the group screen's mark; null, as on a first visit, makes nothing new. */
    fun since(mark: Long?) {
        seenMark.value = mark
    }

    private fun SharedEvent.entry(group: SharedGroup): SharedEntryUiModel {
        val text = AppText.copy.shared.log
        val author = authorMemberId?.let { group.displayName(it) } ?: AppText.copy.shared.someone
        val time = atEpochMillis.timeOfDayIn(HomeTime)
        return when (this) {
            is SharedEvent.ExpenseAdded -> {
                val (said, tone) = expense.forMe(group)
                SharedEntryUiModel(
                    id = expense.versionId,
                    kind = SharedEntryKind.Added,
                    // The row is about the action, not the category: a plus for an add, a loop for a repeat.
                    icon = if (expense.cause == ExpenseCause.REPEAT) "sync" else "add",
                    // #182: a repeat adds itself, so nobody is named as having added it.
                    title = if (expense.cause == ExpenseCause.REPEAT) {
                        text.addedAutomatically(expense.description, group.name)
                    } else {
                        text.added(author, expense.description, group.name)
                    },
                    before = null,
                    after = null,
                    line = listOfNotNull(said ?: text.notInvolved, time).joinToString(Separators.DOT),
                    tone = tone,
                    expenseId = expense.id,
                )
            }

            is SharedEvent.ExpenseEdited -> {
                val oldAmount = formatMinor(before.amountMinor, Currencies.of(before.currency))
                val newAmount = formatMinor(after.amountMinor, Currencies.of(after.currency))
                SharedEntryUiModel(
                    id = after.versionId,
                    kind = SharedEntryKind.Edited,
                    icon = "edit",
                    title = text.edited(author, after.description),
                    before = oldAmount.takeIf { it != newAmount },
                    after = newAmount.takeIf { it != oldAmount },
                    line = when {
                        // #186: the purchase changed in History and the split followed it.
                        after.cause == ExpenseCause.TRANSACTION && oldAmount != newAmount -> text.splitFollowed(time)

                        after.cause == ExpenseCause.TRANSACTION -> text.changedInHistory(time)

                        oldAmount == newAmount -> text.detailsChanged(time)

                        else -> time
                    },
                    tone = BalanceTone.Settled,
                    expenseId = after.id,
                )
            }

            is SharedEvent.ExpenseRemoved -> {
                val (said, _) = last.forMe(group)
                SharedEntryUiModel(
                    id = removal.versionId,
                    kind = SharedEntryKind.Removed,
                    icon = "delete",
                    title = text.removed(author, last.description),
                    before = null,
                    after = null,
                    line = listOfNotNull(said?.let(text.itWas), time).joinToString(Separators.DOT),
                    tone = BalanceTone.Settled,
                )
            }

            is SharedEvent.PaymentMade -> {
                val from = group.displayName(payment.fromMemberId)
                val to = group.displayName(payment.toMemberId)
                SharedEntryUiModel(
                    id = payment.id,
                    kind = SharedEntryKind.Payment,
                    icon = "cash",
                    title = text.paid(from, to, payment.amount.formatCompact(group.moneyFormat()), payment.method.inSentence()),
                    before = null,
                    after = null,
                    line = text.recordedBy(author, time),
                    tone = if (group.isMe(payment.toMemberId)) BalanceTone.Owed else BalanceTone.Settled,
                    paymentId = payment.id,
                )
            }

            // #218: which payment goes on the line under, so the sentence stays short in both languages.
            is SharedEvent.PaymentRemovalChanged -> SharedEntryUiModel(
                id = removal.id,
                kind = if (removal.removed) SharedEntryKind.Removed else SharedEntryKind.Payment,
                icon = if (removal.removed) "delete" else "cash",
                title = if (removal.removed) text.paymentRemoved(author) else text.paymentPutBack(author),
                before = null,
                after = null,
                line = text.whichPayment(
                    group.displayName(payment.fromMemberId),
                    group.displayName(payment.toMemberId),
                    payment.amount.formatCompact(group.moneyFormat()),
                    time,
                ),
                tone = BalanceTone.Settled,
            )
        }
    }

    /** What an expense means for you, in the group's money, or null when you are not in it; and its tone. */
    private fun SharedExpense.forMe(group: SharedGroup): Pair<String?, BalanceTone> {
        val effect = effectFor(group.meMemberId)
        val text = AppText.copy.shared.log
        val money = group.moneyFormat()
        return when {
            effect < Satang.Zero -> text.youOwePayer(group.displayName(payerMemberId), effect.magnitude().formatCompact(money)) to BalanceTone.Owe
            effect > Satang.Zero -> text.youLent(effect.formatCompact(money)) to BalanceTone.Owed
            else -> null to BalanceTone.Settled
        }
    }
}

private fun PaymentMethod.inSentence(): String {
    val text = AppText.copy.shared.log
    return when (this) {
        PaymentMethod.BankTransfer -> text.byBankTransfer
        PaymentMethod.Cash -> text.inCash
        PaymentMethod.PromptPay -> text.byPromptPay
        PaymentMethod.OtherApp -> text.inAnotherApp
    }
}
