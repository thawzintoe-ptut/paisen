package com.ptut.expense.domain.shared

import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Where Add expense starts (#183). */
sealed interface ExpenseStart {
    val group: SharedGroup

    /** A new expense: you paying, split equally. */
    data class New(override val group: SharedGroup) : ExpenseStart

    /** #186: a purchase from History, shared from its detail screen. */
    data class FromTransaction(override val group: SharedGroup, val seed: TransactionSeed) : ExpenseStart

    /** An edit from the latest version, a removed one too. O4: a [linked] expense's amount, date and payer change in History only. */
    data class Existing(override val group: SharedGroup, val expense: SharedExpense, val linked: Boolean) : ExpenseStart
}

/** Waits for the group to be on this phone, then says where Add expense starts. */
class LoadExpenseDraftUseCase(private val repository: SharedRepository) {
    suspend operator fun invoke(groupId: String, expenseId: String?, transactionId: String?): ExpenseStart? {
        val snapshot = repository.snapshot.first { it.group(groupId) != null }
        val group = snapshot.group(groupId) ?: return null
        val existing = expenseId?.let { snapshot.latestVersionOf(groupId, it) }
        val seed = transactionId?.let { repository.transactionSeed(it) }
        return when {
            existing == null && seed != null -> ExpenseStart.FromTransaction(group, seed)
            existing == null -> ExpenseStart.New(group)
            else -> ExpenseStart.Existing(group, existing, linked = existing.id in snapshot.links)
        }
    }
}

/** Where the repeat sheet starts (#185): the repeat being edited, or null for a new one that starts on [firstOfNextMonth]. */
data class RepeatStart(val group: SharedGroup, val rule: RepeatRule?, val firstOfNextMonth: LocalDate)

/** Waits for the group to be on this phone, then reads the repeat being edited. */
class LoadRepeatDraftUseCase(
    private val repository: SharedRepository,
    private val homeToday: GetHomeTodayUseCase,
) {
    suspend operator fun invoke(groupId: String, ruleId: String?): RepeatStart? {
        val snapshot = repository.snapshot.first { it.group(groupId) != null }
        val group = snapshot.group(groupId) ?: return null
        val today = homeToday()
        return RepeatStart(
            group = group,
            rule = ruleId?.let { id -> snapshot.repeats.firstOrNull { it.id == id } },
            firstOfNextMonth = LocalDate(today.year, today.month, 1).plus(DatePeriod(months = 1)),
        )
    }
}
