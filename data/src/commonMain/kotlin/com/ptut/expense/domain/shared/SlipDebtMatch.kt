package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Home
import com.ptut.expense.domain.Satang

/** A debt this phone's member owes in one group, which a slip for the same amount may have paid. */
data class SlipDebt(
    val groupId: String,
    val groupName: String,
    val fromMemberId: String,
    val toMemberId: String,
    val toName: String,
    val amount: Satang,
)

/**
 * Every debt I owe of exactly [amount], as Who pays whom shows it. The amount decides; the [payee]
 * a slip printed only orders the list, because a K PLUS name is often a full legal name or a shop.
 * #48: only groups counting in the slip's [currency], since K29,500 is not ฿29,500. The caller
 * passes the slip's currency; the home is only a stand-in for a call that has none.
 */
fun SharedSnapshot.debtsPaidBy(amount: Satang, payee: String?, currency: String = Home.money.code): List<SlipDebt> {
    val slipWords = payee.words()
    return groups.filter { it.currency == currency }.flatMap { group ->
        val me = group.meMemberId ?: return@flatMap emptyList()
        whoPaysWhom(group.id).transfers
            .filter { it.fromMemberId == me && it.amount == amount }
            .map { SlipDebt(group.id, group.name, me, it.toMemberId, group.member(it.toMemberId)?.name.orEmpty(), it.amount) }
    }.sortedByDescending { debt -> debt.toName.words().any { it in slipWords } }
}

private fun String?.words(): Set<String> =
    orEmpty().lowercase().split(' ', '.', ',').filter { it.length >= MIN_WORD }.toSet()

/** "Mr" and single initials say nothing about who was paid. */
private const val MIN_WORD = 3
