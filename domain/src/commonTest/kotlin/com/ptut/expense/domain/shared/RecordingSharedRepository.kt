package com.ptut.expense.domain.shared

import com.ptut.expense.domain.TransactionEdit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate

/** Records the calls a use case makes. Anything a test does not expect fails it. */
class RecordingSharedRepository(snapshot: SharedSnapshot = SharedSnapshot.Empty) : SharedRepository {

    override val snapshot = MutableStateFlow(snapshot)

    val createdGroups = mutableListOf<Triple<String, GroupType, List<NewMember>>>()
    val createdIcons = mutableListOf<String>()
    val promptPays = mutableListOf<Pair<String, String?>>()
    val dueRepeatDays = mutableListOf<LocalDate>()
    val savedExpenses = mutableListOf<ExpenseDraft>()
    val payments = mutableListOf<PaymentDraft>()
    val savedRepeats = mutableListOf<RepeatRule>()
    val calls = mutableListOf<String>()

    /** Thrown by the next write, then cleared. */
    var failNext: Exception? = null

    private fun write(call: String) {
        failNext?.let {
            failNext = null
            throw it
        }
        calls += call
    }

    override suspend fun createGroup(name: String, type: GroupType, icon: String, members: List<NewMember>, currency: String): String {
        createdGroups += Triple(name, type, members)
        createdIcons += icon
        return "new-group"
    }

    override suspend fun setPromptPay(groupId: String, promptPayId: String?) {
        promptPays += groupId to promptPayId
    }

    override suspend fun addDueRepeats(today: LocalDate): Int {
        dueRepeatDays += today
        return 0
    }

    override suspend fun addMembers(groupId: String, members: List<NewMember>) = notThisTest()

    override suspend fun setSimplifyDebts(groupId: String, on: Boolean) = notThisTest()

    override suspend fun editGroup(groupId: String, name: String, type: GroupType, icon: String) = notThisTest()

    override suspend fun deleteGroup(groupId: String) = write("deleteGroup $groupId")

    override suspend fun setPersonArchived(personId: String, archived: Boolean) = notThisTest()

    override suspend fun removeMember(groupId: String, memberId: String) = notThisTest()

    override suspend fun saveExpense(draft: ExpenseDraft): String {
        write("saveExpense")
        savedExpenses += draft
        return draft.expenseId ?: "new-expense"
    }

    override suspend fun removeExpense(expenseId: String) = write("removeExpense $expenseId")

    override suspend fun recordPayment(draft: PaymentDraft): String {
        write("recordPayment")
        payments += draft
        return "new-payment"
    }

    override suspend fun transactionSeed(transactionId: String): TransactionSeed? = notThisTest()

    override suspend fun followTransaction(transactionId: String, before: TransactionEdit, titleChange: TitleChange?) = write("followTransaction $transactionId $titleChange")

    override suspend fun removePayment(paymentId: String) = write("removePayment $paymentId")

    override suspend fun putBackPayment(paymentId: String) = write("putBackPayment $paymentId")

    override suspend fun restoreExpense(expenseId: String) = write("restoreExpense $expenseId")

    override suspend fun relink(expenseId: String, transactionId: String) = write("relink $expenseId $transactionId")

    override suspend fun saveRepeat(rule: RepeatRule): String {
        write("saveRepeat")
        savedRepeats += rule
        return rule.id.ifEmpty { "new-repeat" }
    }

    override suspend fun stopRepeat(ruleId: String) = write("stopRepeat $ruleId")

    override suspend fun setRepeatReminder(ruleId: String, on: Boolean) = notThisTest()

    override suspend fun lastRate(currency: String, into: String): Rate? = notThisTest()

    override suspend fun markSeen(groupId: String, atEpochMillis: Long) = notThisTest()

    private fun notThisTest(): Nothing = error("Not expected in this test")

    override suspend fun setStampsOff(off: Boolean) = write("setStampsOff $off")
}
