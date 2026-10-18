package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang

/**
 * v2's shared costs (#133). Every id is a UUID so a group can later sync between phones (#189)
 * without two phones minting the same key, and every log row says who added it and when.
 */

/** p34's chips. Each one fills in a name and an icon; [Other] is a group named by hand. */
enum class GroupType { Trip, Flat, Couple, Other }

/** p29: four ways to split one total. */
enum class SplitMode { Equally, Exact, Percent, Shares }

/** p31's chips. A label only: no money moves through PaiSen. */
enum class PaymentMethod { BankTransfer, Cash, PromptPay, OtherApp }

/** A name on this phone that can belong to no group or many (#56). */
data class Person(val id: String, val name: String, val archived: Boolean = false)

/** A name inside one group. [personId] ties it to the same [Person] in other groups. */
data class Member(
    val id: String,
    val groupId: String,
    val name: String,
    val personId: String?,
    val archived: Boolean = false,
    /** #190: the account that claimed this name. Null while it is only a name. */
    val claimedByUid: String? = null,
    /** #199: the PromptPay ID this member added for their own name, for the payer's QR. */
    val promptPayId: String? = null,
)

data class SharedGroup(
    val id: String,
    val name: String,
    val type: GroupType,
    /** A [com.ptut.expense.design.PtutIcon] name, held as a string like a category's glyph. */
    val icon: String,
    /** p30: on by default. Off shows the original pairings. */
    val simplifyDebts: Boolean,
    val createdAtEpochMillis: Long,
    /** Which member this phone is. Kept on the phone, never synced: each member's phone has its own. */
    val meMemberId: String?,
    val members: List<Member>,
    /** #189: synced with the people in it. False until it is first shared. */
    val remote: Boolean = false,
    val creatorUid: String? = null,
    val inviteCode: String? = null,
    val inviteActive: Boolean = false,
    val lastSyncedAtEpochMillis: Long? = null,
    /** Research finding 5: the newest row this phone showed, so rows others add after it get a dot. */
    val lastSeenAtEpochMillis: Long? = null,
) {
    val activeMembers: List<Member> get() = members.filterNot { it.archived }

    fun member(id: String): Member? = members.firstOrNull { it.id == id }

    fun isMe(memberId: String): Boolean = memberId == meMemberId
}

/**
 * One member's part of an expense. [weight] is what the mode reads (an exact amount, basis points
 * of a percent, or a count of shares); [amountMinor] is their part in the expense's currency and
 * [satang] the same part in baht at the saved rate.
 */
data class Share(val memberId: String, val weight: Long, val amountMinor: Long, val satang: Satang)

/**
 * One version of a shared expense. An edit writes a new version and never changes an old one, so
 * Activity can show "$220.00 → $240.00". A removal is a version with [removed] set (D8).
 */
data class SharedExpense(
    val id: String,
    val versionId: String,
    val version: Int,
    val groupId: String,
    val description: String,
    val occurredAtEpochMillis: Long,
    val categoryId: String?,
    val subCategoryId: String?,
    val note: String?,
    val currency: String,
    val amountMinor: Long,
    /** Baht per one unit of [currency]. Null for THB. */
    val rate: Rate?,
    /** The whole amount in baht at [rate]. */
    val satang: Satang,
    val payerMemberId: String,
    val splitMode: SplitMode,
    val shares: List<Share>,
    val authorMemberId: String?,
    val createdAtEpochMillis: Long,
    val removed: Boolean,
    /** Set when the app wrote this version itself, see [ExpenseCause]. */
    val cause: String? = null,
)

/** Why a version was written without anyone typing it, for Activity's wording (#140). */
object ExpenseCause {
    /** #186: your own transaction changed in History, and the shared copy followed. */
    const val TRANSACTION = "transaction"

    /** #182: a repeat added itself on its day. */
    const val REPEAT = "repeat"
}

/** #186: what a transaction in History brings to Add expense, locked there because History owns it. */
data class TransactionSeed(
    val transactionId: String,
    val amount: Satang,
    val occurredAtEpochMillis: Long,
    val description: String,
    val categoryId: String?,
    val subCategoryId: String?,
)

/** p31: a payment between two members. It never edits an expense. */
data class SharedPayment(
    val id: String,
    val groupId: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amount: Satang,
    val method: PaymentMethod,
    val occurredAtEpochMillis: Long,
    val authorMemberId: String?,
    val createdAtEpochMillis: Long,
)

/** #218: a payment taken out of a shared group's balances ([removed]), or put back. The latest decides. */
data class PaymentRemoval(
    val id: String,
    val groupId: String,
    val paymentId: String,
    val removed: Boolean,
    val authorMemberId: String?,
    val createdAtEpochMillis: Long,
)

/**
 * Everything v2 holds, read in one go. Groups are small, so screens derive what they show from
 * this with the pure functions in [Balances] rather than asking the database twice.
 */
data class SharedSnapshot(
    val groups: List<SharedGroup>,
    val people: List<Person>,
    /** Every version of every expense, oldest first. */
    val expenseVersions: List<SharedExpense>,
    val payments: List<SharedPayment>,
    /** Expense id to your own transaction's id, for the expenses you paid (#32). Never synced. */
    val links: Map<String, String>,
    /** #182: every repeat, stopped ones too. */
    val repeats: List<RepeatRule> = emptyList(),
    /** #218: every removal and put-back of a payment, oldest first. */
    val paymentRemovals: List<PaymentRemoval> = emptyList(),
) {
    fun group(id: String): SharedGroup? = groups.firstOrNull { it.id == id }

    companion object {
        val Empty = SharedSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())
    }
}

/** A member to add: an existing person, or a new name that becomes one. */
data class NewMember(val name: String, val personId: String? = null)

/** What Add expense saves. [expenseId] null is a new expense; set, it writes the next version. */
data class ExpenseDraft(
    val expenseId: String?,
    val groupId: String,
    val description: String,
    val occurredAtEpochMillis: Long,
    val categoryId: String?,
    val subCategoryId: String?,
    val note: String?,
    val currency: String,
    val amountMinor: Long,
    val rate: Rate?,
    val payerMemberId: String,
    val splitMode: SplitMode,
    /** Member id to weight, in the mode's own unit. */
    val weights: Map<String, Long>,
    /** #186: share this existing transaction rather than writing a new one for "you paid". */
    val linkTransactionId: String? = null,
    /** The version an edit opened on. Set, a save stops once when another phone changed or removed the expense since. */
    val baseVersionId: String? = null,
)

/** An edit's expense moved on since the form opened: [latestVersionId] is what is there now. */
class ExpenseChanged(val latestVersionId: String, val removed: Boolean) : Exception("The expense changed since it was opened")

data class PaymentDraft(
    val groupId: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amount: Satang,
    val method: PaymentMethod,
    val occurredAtEpochMillis: Long,
)

/** #186: the shared expense [transactionId] is linked to, while it is still in its group. */
fun SharedSnapshot.sharedExpenseOf(transactionId: String): SharedExpense? {
    val expenseId = links.entries.firstOrNull { it.value == transactionId }?.key ?: return null
    return expenseVersions.lastOrNull { it.id == expenseId }?.takeUnless { it.removed }
}
