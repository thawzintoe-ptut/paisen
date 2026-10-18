package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.sum

/**
 * p36: "Balances are derived, never stored." Everything here is computed from the log in a
 * [SharedSnapshot], so a figure on any v2 screen can be traced back to the rows that made it.
 */

/** The latest version of each expense, with the removed ones left out (D8). */
fun SharedSnapshot.currentExpenses(groupId: String? = null): List<SharedExpense> =
    expenseVersions
        .filter { groupId == null || it.groupId == groupId }
        .groupBy { it.id }
        .values
        .map { versions -> versions.maxWith(LogOrder) }
        .filterNot { it.removed }
        .sortedByDescending { it.occurredAtEpochMillis }

/** The payments that count: one whose latest removal row takes it out is left out (#218). */
fun SharedSnapshot.paymentsIn(groupId: String): List<SharedPayment> {
    val removed = removedPaymentIds()
    return payments.filter { it.groupId == groupId && it.id !in removed }.sortedByDescending { it.occurredAtEpochMillis }
}

/** #218: the payments whose latest removal row, in [RemovalOrder], says removed. */
fun SharedSnapshot.removedPaymentIds(): Set<String> =
    paymentRemovals
        .groupBy { it.paymentId }
        .values
        .map { rows -> rows.maxWith(RemovalOrder) }
        .filter { it.removed }
        .mapTo(mutableSetOf()) { it.paymentId }

/** Each member's net in a group: above zero they are owed, below zero they owe. */
fun SharedSnapshot.nets(groupId: String): Map<String, Satang> {
    val net = mutableMapOf<String, Satang>()
    fun add(member: String, amount: Satang) {
        net[member] = (net[member] ?: Satang.Zero) + amount
    }
    currentExpenses(groupId).forEach { expense ->
        add(expense.payerMemberId, expense.satang)
        expense.shares.forEach { add(it.memberId, -it.satang) }
    }
    paymentsIn(groupId).forEach { payment ->
        add(payment.fromMemberId, payment.amount)
        add(payment.toMemberId, -payment.amount)
    }
    return net
}

/** One payment that settles part of a group: [fromMemberId] pays [toMemberId]. */
data class Transfer(val fromMemberId: String, val toMemberId: String, val amount: Satang)

/**
 * Who owes whom before simplifying: each pair's debts in both directions netted to one transfer.
 * A payment lowers what its payer owed the other person.
 */
fun SharedSnapshot.pairwise(groupId: String): List<Transfer> {
    val owes = mutableMapOf<Pair<String, String>, Long>()
    fun owe(debtor: String, creditor: String, satang: Long) {
        if (debtor == creditor) return
        owes[debtor to creditor] = (owes[debtor to creditor] ?: 0L) + satang
    }
    currentExpenses(groupId).forEach { expense ->
        expense.shares.forEach { owe(it.memberId, expense.payerMemberId, it.satang.toMinorUnits()) }
    }
    paymentsIn(groupId).forEach { owe(it.fromMemberId, it.toMemberId, -it.amount.toMinorUnits()) }
    val pairs = owes.keys.map { (a, b) -> if (a < b) a to b else b to a }.distinct().sortedWith(compareBy({ it.first }, { it.second }))
    return pairs.mapNotNull { (a, b) ->
        val net = (owes[a to b] ?: 0L) - (owes[b to a] ?: 0L)
        when {
            net > 0 -> Transfer(a, b, Satang.ofMinorUnits(net))
            net < 0 -> Transfer(b, a, Satang.ofMinorUnits(-net))
            else -> null
        }
    }
}

/** p30: the payments to show, how many there were between everyone, and how many after simplifying. */
data class Settlement(val transfers: List<Transfer>, val pairwiseCount: Int, val simplifiedCount: Int)

fun SharedSnapshot.whoPaysWhom(groupId: String): Settlement {
    val group = group(groupId) ?: return Settlement(emptyList(), 0, 0)
    val pairwise = pairwise(groupId)
    val simplified = simplify(nets(groupId), group.splitMembers())
    return Settlement(
        transfers = if (group.simplifyDebts) simplified else pairwise,
        pairwiseCount = pairwise.size,
        simplifiedCount = simplified.size,
    )
}

fun SharedGroup.splitMembers(): List<SplitMember> = members.map { SplitMember(it.id, it.name, isMe(it.id)) }

/**
 * Who a new split goes over: the people still in the group, plus [keeping]. An edit keeps everyone
 * with a part in the version it replaces, so fixing a description never drops a removed name's share.
 */
fun SharedGroup.membersToSplit(keeping: Set<String> = emptySet()): List<SplitMember> =
    splitMembers().filter { member(it.id)?.archived != true || it.id in keeping }

/** What this phone's member is owed (above zero) or owes (below zero) in a group. */
fun SharedSnapshot.myNet(groupId: String): Satang {
    val me = group(groupId)?.meMemberId ?: return Satang.Zero
    return nets(groupId)[me] ?: Satang.Zero
}

/** Each other member's balance with you in a group: above zero they owe you, below zero you owe them. */
fun SharedSnapshot.withMe(groupId: String): Map<String, Satang> {
    val me = group(groupId)?.meMemberId ?: return emptyMap()
    val balance = mutableMapOf<String, Satang>()
    whoPaysWhom(groupId).transfers.forEach { transfer ->
        when (me) {
            transfer.toMemberId -> balance[transfer.fromMemberId] = (balance[transfer.fromMemberId] ?: Satang.Zero) + transfer.amount
            transfer.fromMemberId -> balance[transfer.toMemberId] = (balance[transfer.toMemberId] ?: Satang.Zero) - transfer.amount
        }
    }
    return balance
}

/** p26's People: one balance per person across every group, matched by person, then by the account that claimed the name, else by name. */
data class PersonBalance(val key: String, val name: String, val amount: Satang, val groupIds: List<String>)

fun SharedSnapshot.peopleBalances(): List<PersonBalance> {
    val rows = mutableMapOf<String, PersonBalance>()
    groups.forEach { group ->
        val balances = withMe(group.id)
        group.members.filterNot { group.isMe(it.id) }.forEach { member ->
            // A name pulled from a shared group has no person here: the account that claimed it is
            // the same person in every group, where two "Tom"s in two groups may not be.
            val key = member.personId ?: member.claimedByUid?.let { "uid:$it" } ?: "name:${member.name.lowercase()}"
            val amount = balances[member.id] ?: Satang.Zero
            val row = rows[key]
            rows[key] = if (row == null) {
                PersonBalance(key, member.name, amount, listOf(group.id))
            } else {
                row.copy(amount = row.amount + amount, groupIds = row.groupIds + group.id)
            }
        }
    }
    return rows.values.sortedWith(compareByDescending<PersonBalance> { it.amount }.thenBy { it.name.lowercase() })
}

/** p26's top: the net, and the two totals it is made of. */
data class Overall(val net: Satang, val owedToYou: Satang, val youOwe: Satang)

fun SharedSnapshot.overall(): Overall {
    val people = peopleBalances().map { it.amount }
    val owed = people.filter { it > Satang.Zero }.sum()
    val owe = -people.filter { it < Satang.Zero }.sum()
    return Overall(net = owed - owe, owedToYou = owed, youOwe = owe)
}

/** The currencies a group has used, baht first: "THB, USD, VND". */
fun SharedSnapshot.currenciesOf(groupId: String): List<String> =
    currentExpenses(groupId).map { it.currency }.distinct().sortedWith(compareBy({ it != Currencies.Thb.code }, { it }))

/** p27's "Total trip spend": every current expense in baht. */
fun SharedSnapshot.totalSpend(groupId: String): Satang = currentExpenses(groupId).map { it.satang }.sum()

/** One line of p32's timeline. */
sealed interface SharedEvent {
    val groupId: String
    val atEpochMillis: Long
    val authorMemberId: String?

    data class ExpenseAdded(val expense: SharedExpense) : SharedEvent {
        override val groupId get() = expense.groupId
        override val atEpochMillis get() = expense.createdAtEpochMillis
        override val authorMemberId get() = expense.authorMemberId
    }

    data class ExpenseEdited(val before: SharedExpense, val after: SharedExpense) : SharedEvent {
        override val groupId get() = after.groupId
        override val atEpochMillis get() = after.createdAtEpochMillis
        override val authorMemberId get() = after.authorMemberId
    }

    data class ExpenseRemoved(val last: SharedExpense, val removal: SharedExpense) : SharedEvent {
        override val groupId get() = removal.groupId
        override val atEpochMillis get() = removal.createdAtEpochMillis
        override val authorMemberId get() = removal.authorMemberId
    }

    data class PaymentMade(val payment: SharedPayment) : SharedEvent {
        override val groupId get() = payment.groupId
        override val atEpochMillis get() = payment.createdAtEpochMillis
        override val authorMemberId get() = payment.authorMemberId
    }

    /** #218: a payment taken out of the balances ([PaymentRemoval.removed]) or put back. */
    data class PaymentRemovalChanged(val payment: SharedPayment, val removal: PaymentRemoval) : SharedEvent {
        override val groupId get() = removal.groupId
        override val atEpochMillis get() = removal.createdAtEpochMillis
        override val authorMemberId get() = removal.authorMemberId
    }
}

/** Every change, newest first: a version 1 is an add, a later one an edit or a removal (#140). */
fun SharedSnapshot.events(groupId: String? = null): List<SharedEvent> {
    val versions = expenseVersions.filter { groupId == null || it.groupId == groupId }
    val expenseEvents = versions.groupBy { it.id }.values.flatMap { history ->
        val ordered = history.sortedWith(LogOrder)
        ordered.mapIndexed { index, version ->
            val before = ordered.getOrNull(index - 1)
            when {
                before == null -> SharedEvent.ExpenseAdded(version)
                version.removed -> SharedEvent.ExpenseRemoved(before, version)
                else -> SharedEvent.ExpenseEdited(before, version)
            }
        }
    }
    val paymentEvents = payments.filter { groupId == null || it.groupId == groupId }.map { SharedEvent.PaymentMade(it) }
    val byId = payments.associateBy { it.id }
    val removalEvents = paymentRemovals
        .filter { groupId == null || it.groupId == groupId }
        .mapNotNull { removal -> byId[removal.paymentId]?.let { SharedEvent.PaymentRemovalChanged(it, removal) } }
    return (expenseEvents + paymentEvents + removalEvents).sortedByDescending { it.atEpochMillis }
}

/**
 * #192: the one order every phone puts an expense's versions in. Not the version number: two
 * phones editing offline both write the next number, and both edits stay in the log.
 */
val LogOrder: Comparator<SharedExpense> = compareBy<SharedExpense>({ it.createdAtEpochMillis }, { it.versionId })

/** #218: the same order for a payment's removal rows, so every phone agrees on the latest one. */
val RemovalOrder: Comparator<PaymentRemoval> = compareBy<PaymentRemoval>({ it.createdAtEpochMillis }, { it.id })
