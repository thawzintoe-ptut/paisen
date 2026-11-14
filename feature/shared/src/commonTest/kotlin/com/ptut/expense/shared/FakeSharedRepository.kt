package com.ptut.expense.shared

import com.ptut.expense.domain.shared.ExpenseDraft
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.PaymentDraft
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.TransactionSeed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate

/** "Flat 12": this phone's member and Aye, nothing spent yet. */
internal val flat12 = SharedGroup(
    id = "g1",
    name = "Flat 12",
    type = GroupType.Flat,
    icon = "home",
    simplifyDebts = true,
    createdAtEpochMillis = 0,
    meMemberId = "me",
    members = listOf(
        Member(id = "me", groupId = "g1", name = "Ko Ko", personId = null),
        Member(id = "aye", groupId = "g1", name = "Aye", personId = null),
    ),
)

/**
 * One group and the saves the screens make. Each save is counted when it starts, then waits on
 * [gate] as a slow write would, so a second tap lands while the first is still writing.
 */
internal class FakeSharedRepository(group: SharedGroup = flat12) : SharedRepository {

    override val snapshot = MutableStateFlow(
        SharedSnapshot(groups = listOf(group), people = emptyList(), expenseVersions = emptyList(), payments = emptyList(), links = emptyMap()),
    )

    val gate = CompletableDeferred<Unit>()
    val expenses = mutableListOf<ExpenseDraft>()
    val payments = mutableListOf<PaymentDraft>()
    val repeats = mutableListOf<RepeatRule>()

    override suspend fun saveExpense(draft: ExpenseDraft): String {
        expenses += draft
        gate.await()
        return "expense-${expenses.size}"
    }

    override suspend fun recordPayment(draft: PaymentDraft): String {
        payments += draft
        gate.await()
        return "payment-${payments.size}"
    }

    override suspend fun saveRepeat(rule: RepeatRule): String {
        repeats += rule
        gate.await()
        return "repeat-${repeats.size}"
    }

    override suspend fun transactionSeed(transactionId: String): TransactionSeed? = null

    override suspend fun lastRate(currency: String): Rate? = null

    override suspend fun markSeen(groupId: String, atEpochMillis: Long) = Unit

    override suspend fun createGroup(name: String, type: GroupType, icon: String, members: List<NewMember>): String = notThisTest()

    override suspend fun addMembers(groupId: String, members: List<NewMember>) = notThisTest<Unit>()

    override suspend fun setSimplifyDebts(groupId: String, on: Boolean) = notThisTest<Unit>()

    override suspend fun setPromptPay(groupId: String, promptPayId: String?) = notThisTest<Unit>()

    override suspend fun editGroup(groupId: String, name: String, type: GroupType, icon: String) = notThisTest<Unit>()

    override suspend fun deleteGroup(groupId: String) = notThisTest<Unit>()

    override suspend fun setPersonArchived(personId: String, archived: Boolean) = notThisTest<Unit>()

    override suspend fun removeMember(groupId: String, memberId: String) = notThisTest<Unit>()

    override suspend fun removeExpense(expenseId: String) = notThisTest<Unit>()

    override suspend fun followTransaction(transactionId: String) = notThisTest<Unit>()

    override suspend fun removePayment(paymentId: String) = notThisTest<Unit>()

    override suspend fun putBackPayment(paymentId: String) = notThisTest<Unit>()

    override suspend fun restoreExpense(expenseId: String) = notThisTest<Unit>()

    override suspend fun relink(expenseId: String, transactionId: String) = notThisTest<Unit>()

    override suspend fun stopRepeat(ruleId: String) = notThisTest<Unit>()

    override suspend fun setRepeatReminder(ruleId: String, on: Boolean) = notThisTest<Unit>()

    override suspend fun addDueRepeats(today: LocalDate): Int = notThisTest()

    private fun <T> notThisTest(): T = throw AssertionError("The save screens never call this.")
}
