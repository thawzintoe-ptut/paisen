package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.ExpenseCause
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.SharedEvent
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.events
import com.ptut.expense.domain.shared.formatMinor
import com.ptut.expense.domain.sum
import com.ptut.expense.domain.timeOfDayIn
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

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
)

data class SharedActivityDay(val label: String, val entries: List<SharedEntryUiModel>)

data class SharedActivityUiState(val title: String, val filter: ActivityFilter, val days: List<SharedActivityDay>)

/**
 * #140: one timeline of everything shared, newest first. Edits keep the old value beside the new
 * one, so a changed total can always be explained, and every entry says who added it.
 */
class SharedActivityViewModel(
    private val groupId: String?,
    repository: SharedRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val filter = MutableStateFlow(ActivityFilter.All)

    val uiState: StateFlow<SharedActivityUiState> = combine(repository.snapshot, filter) { snapshot, filter ->
        val today = clock.now().toLocalDateTime(BangkokTime).date
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
        SharedActivityUiState(
            title = groupId?.let { snapshot.group(it)?.name?.let(text.log.groupTitle) } ?: text.activity,
            filter = filter,
            days = entries
                .groupBy { (event, _) -> Instant.fromEpochMilliseconds(event.atEpochMillis).dateIn(BangkokTime) }
                .map { (date, rows) ->
                    SharedActivityDay(
                        label = if (date == today) AppText.copy.common.today.uppercase() else date.dayLabel().uppercase(),
                        entries = rows.map { (event, group) -> event.entry(group) },
                    )
                },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SharedActivityUiState(AppText.copy.shared.activity, ActivityFilter.All, emptyList()))

    fun show(filter: ActivityFilter) {
        this.filter.value = filter
    }

    private fun SharedEvent.entry(group: SharedGroup): SharedEntryUiModel {
        val text = AppText.copy.shared.log
        val author = authorMemberId?.let { group.displayName(it) } ?: AppText.copy.shared.someone
        val time = atEpochMillis.timeOfDayIn(BangkokTime)
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
                    line = listOfNotNull(said ?: text.notInvolved, time).joinToString(" · "),
                    tone = tone,
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
                    line = listOfNotNull(said?.let(text.itWas), time).joinToString(" · "),
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
                    title = text.paid(from, to, payment.amount.formatCompact(), payment.method.inSentence()),
                    before = null,
                    after = null,
                    line = text.recordedBy(author, time),
                    tone = if (group.isMe(payment.toMemberId)) BalanceTone.Owed else BalanceTone.Settled,
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
                    payment.amount.formatCompact(),
                    time,
                ),
                tone = BalanceTone.Settled,
            )
        }
    }

    /** What an expense means for you, with the amount in baht, or null when you are not in it; and its tone. */
    private fun SharedExpense.forMe(group: SharedGroup): Pair<String?, BalanceTone> {
        val me = group.meMemberId
        val mine = shares.filter { it.memberId == me }.map { it.satang }.sum()
        val effect = (if (payerMemberId == me) satang else Satang.Zero) - mine
        val text = AppText.copy.shared.log
        return when {
            effect < Satang.Zero -> text.youOwePayer(group.displayName(payerMemberId), effect.magnitude().formatCompact()) to BalanceTone.Owe
            effect > Satang.Zero -> text.youLent(effect.formatCompact()) to BalanceTone.Owed
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
