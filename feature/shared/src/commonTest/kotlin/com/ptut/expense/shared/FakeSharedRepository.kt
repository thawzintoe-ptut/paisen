package com.ptut.expense.shared

import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.shared.ExpenseDraft
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.PaymentDraft
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.Share
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.SplitMode
import com.ptut.expense.domain.shared.TitleChange
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

/** "Yangon trip": the same two people counting in kyat (#48). */
internal val yangonTrip = flat12.copy(
    id = "g2",
    name = "Yangon trip",
    type = GroupType.Trip,
    icon = "work",
    meMemberId = "me2",
    members = listOf(
        Member(id = "me2", groupId = "g2", name = "Ko Ko", personId = null),
        Member(id = "aye2", groupId = "g2", name = "Aye", personId = null),
    ),
    currency = "MMK",
)

/** A dinner in the group's own money, split equally between its two members, so the other owes half. */
internal fun SharedGroup.dinner(paidBy: String, wholeMinor: Long): SharedExpense {
    val whole = Satang.ofMinorUnits(wholeMinor)
    val half = Satang.ofMinorUnits(wholeMinor / 2)
    return SharedExpense(
        id = "$id-dinner",
        versionId = "$id-dinner-v1",
        version = 1,
        groupId = id,
        description = "Dinner",
        occurredAtEpochMillis = 0,
        categoryId = null,
        subCategoryId = null,
        note = null,
        currency = currency,
        amountMinor = wholeMinor,
        rate = null,
        satang = whole,
        payerMemberId = paidBy,
        splitMode = SplitMode.Equally,
        shares = members.map { Share(it.id, 1, half.toMinorUnits(), half) },
        authorMemberId = paidBy,
        createdAtEpochMillis = 0,
        removed = false,
    )
}

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

    override suspend fun lastRate(currency: String, into: String): Rate? = null

    override suspend fun markSeen(groupId: String, atEpochMillis: Long) = Unit

    override suspend fun createGroup(name: String, type: GroupType, icon: String, members: List<NewMember>, currency: String): String = notThisTest()

    override suspend fun addMembers(groupId: String, members: List<NewMember>) = notThisTest<Unit>()

    override suspend fun setSimplifyDebts(groupId: String, on: Boolean) = notThisTest<Unit>()

    override suspend fun setPromptPay(groupId: String, promptPayId: String?) = notThisTest<Unit>()

    override suspend fun editGroup(groupId: String, name: String, type: GroupType, icon: String) = notThisTest<Unit>()

    override suspend fun deleteGroup(groupId: String) = notThisTest<Unit>()

    override suspend fun setPersonArchived(personId: String, archived: Boolean) = notThisTest<Unit>()

    override suspend fun removeMember(groupId: String, memberId: String) = notThisTest<Unit>()

    val removedExpenses = mutableListOf<String>()

    override suspend fun removeExpense(expenseId: String) {
        removedExpenses += expenseId
    }

    override suspend fun followTransaction(transactionId: String, before: TransactionEdit, titleChange: TitleChange?) = notThisTest<Unit>()

    override suspend fun removePayment(paymentId: String) = notThisTest<Unit>()

    override suspend fun putBackPayment(paymentId: String) = notThisTest<Unit>()

    override suspend fun restoreExpense(expenseId: String) = notThisTest<Unit>()

    override suspend fun relink(expenseId: String, transactionId: String) = notThisTest<Unit>()

    override suspend fun stopRepeat(ruleId: String) = notThisTest<Unit>()

    override suspend fun setRepeatReminder(ruleId: String, on: Boolean) = notThisTest<Unit>()

    override suspend fun addDueRepeats(today: LocalDate): Int = notThisTest()

    private fun <T> notThisTest(): T = throw AssertionError("The save screens never call this.")
}
