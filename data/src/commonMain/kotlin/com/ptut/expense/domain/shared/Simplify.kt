package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang

/**
 * #135: a group's debts as the fewest payments that settle it (p30, "three payments instead of six").
 *
 * Greedy: the largest debtor pays the largest creditor until one of them is square, then on to the
 * next. That gives at most n − 1 payments. The true minimum is NP-hard, and greedy is what the
 * apps p30 copies do too. Ties break on D7's order, so the same balances give the same payments
 * on every phone (#189).
 */
fun simplify(nets: Map<String, Satang>, members: List<SplitMember>): List<Transfer> {
    val order = members.inOddUnitOrder().mapIndexed { index, member -> member.id to index }.toMap()
    fun rank(id: String) = order[id] ?: Int.MAX_VALUE

    val creditors = nets.filterValues { it > Satang.Zero }.map { (id, amount) -> id to amount.toMinorUnits() }
        .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { rank(it.first) })
        .toMutableList()
    val debtors = nets.filterValues { it < Satang.Zero }.map { (id, amount) -> id to -amount.toMinorUnits() }
        .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { rank(it.first) })
        .toMutableList()

    val transfers = mutableListOf<Transfer>()
    var c = 0
    var d = 0
    while (c < creditors.size && d < debtors.size) {
        val (creditor, owed) = creditors[c]
        val (debtor, owes) = debtors[d]
        val paid = minOf(owed, owes)
        transfers += Transfer(debtor, creditor, Satang.ofMinorUnits(paid))
        creditors[c] = creditor to owed - paid
        debtors[d] = debtor to owes - paid
        if (creditors[c].second == 0L) c++
        if (debtors[d].second == 0L) d++
    }
    return transfers
}
