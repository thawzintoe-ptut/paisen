package com.ptut.expense.domain.shared

import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

/**
 * v2's shared costs (#133). Everything is a log: an expense edit writes a new version, a removal
 * is a version too, and nothing is updated in place. Until sync lands (#189) the log lives only on
 * this phone, but every row already carries the id and author sync will need.
 */
interface SharedRepository {

    val snapshot: StateFlow<SharedSnapshot>

    /**
     * Makes a group with this phone's member in it (named "You" until sign-in gives a name, #187)
     * and [members]. Returns the group's id.
     */
    suspend fun createGroup(name: String, type: GroupType, icon: String, members: List<NewMember>): String

    suspend fun addMembers(groupId: String, members: List<NewMember>)

    suspend fun setSimplifyDebts(groupId: String, on: Boolean)

    /**
     * #199: this phone's member's own PromptPay ID, or null to take it off. It syncs to the group,
     * so whoever pays this member sees it in a QR.
     */
    suspend fun setPromptPay(groupId: String, promptPayId: String?)

    /** A new name and type (the type sets the icon). Any member may, and it syncs. */
    suspend fun editGroup(groupId: String, name: String, type: GroupType, icon: String)

    /**
     * A group that lives only on this phone goes, with its expenses, payments and repeats. Your own
     * transactions stay in History; only the links to them go. A synced group goes through
     * [com.ptut.expense.sync.SharedSync] instead, which tells Firestore first.
     */
    suspend fun deleteGroup(groupId: String)

    /**
     * #141: an archived person keeps every past split under their name but joins no new one. Their
     * member rows follow, except in a synced group this phone did not create, where only the
     * creator may remove someone (#190).
     */
    suspend fun setPersonArchived(personId: String, archived: Boolean)

    /**
     * #218: one name leaves one group, archived as [setPersonArchived] does, under the same rule.
     * Never this phone's own member.
     */
    suspend fun removeMember(groupId: String, memberId: String)

    /**
     * Writes a new expense, or the next version of one. When this phone's member paid, the first
     * version also records your own transaction for the full amount in baht (#32), and later
     * versions keep that transaction's amount, time and category in step.
     */
    suspend fun saveExpense(draft: ExpenseDraft): String

    /** D8: a removal is a version of its own. Your transaction, if any, stays in History. */
    suspend fun removeExpense(expenseId: String)

    suspend fun recordPayment(draft: PaymentDraft): String

    /** #186: what a transaction in History brings to Add expense. Null when it is gone or is income. */
    suspend fun transactionSeed(transactionId: String): TransactionSeed?

    /**
     * #186, O4: the transaction changed in History, so its shared copy follows: amount, date and
     * category. Equally, Percent and Shares recompute; Exact puts the difference on the payer (O1).
     * Nothing flows back the other way from here.
     */
    suspend fun followTransaction(transactionId: String)

    /**
     * #218: takes a payment out of the balances, also as Undo right after recording it. In a group
     * only on this phone it goes; in a shared one a removal joins the log, so every phone agrees.
     */
    suspend fun removePayment(paymentId: String)

    /** #218: Undo after [removePayment]. */
    suspend fun putBackPayment(paymentId: String)

    /** #186: Undo after a removal. The last version before it is written again, as a new version. */
    suspend fun restoreExpense(expenseId: String)

    /** #186: the link again, once Undo has brought the transaction back. */
    suspend fun relink(expenseId: String, transactionId: String)

    /** #182: a new repeat, or the next version of one. A change applies from the next date. */
    suspend fun saveRepeat(rule: RepeatRule): String

    /** #182: no more dates. What was added stays in the log. */
    suspend fun stopRepeat(ruleId: String)

    /** #182: the day-before reminder, on this phone only. */
    suspend fun setRepeatReminder(ruleId: String, on: Boolean)

    /**
     * #182: adds every date due on [today] (Bangkok), catching up after a gap, each once. A repeat
     * goes in the group's log only, never into your History: your own totals follow your slips.
     * Returns how many entries were new on this phone.
     */
    suspend fun addDueRepeats(today: LocalDate): Int

    /** #181: the last rate used for [currency] on this phone, to pre-fill the next one. */
    suspend fun lastRate(currency: String): Rate?

    /** Research finding 5: the newest row shown while the group is open. Local only, never synced. */
    suspend fun markSeen(groupId: String, atEpochMillis: Long)
}
