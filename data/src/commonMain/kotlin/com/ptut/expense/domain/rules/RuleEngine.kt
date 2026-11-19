package com.ptut.expense.domain.rules

import com.ptut.expense.domain.Direction

/** FR-12, a remembered merchant: this counterparty always lands in this category. */
data class MerchantRule(
    val counterparty: String,
    val categoryId: String,
    /** The rule row's id, stamped on what it files so the record can say why (#117). */
    val id: String = "",
    /** #26: a person can have one rule for money out and another for money in. */
    val direction: Direction = Direction.Expense,
)

/**
 * FR-11: the category a slip lands in, from what the reader has already taught the app. Pure —
 * it holds no rules of its own, so the caller that owns the table decides when they are read.
 *
 * The key is what the slip printed, not a tidied version of it. K PLUS truncates
 * `LAWSON108_P3793 THE LIVIN` and prints it that way every time; a parser that "repaired" the
 * name would key on something no future slip carries, and the rule would never match again.
 *
 * Case and surrounding space are the one thing normalised. A reader that returns the same name in
 * a different case is the same merchant, and treating it as a new one would ask the reader a
 * question they have already answered.
 */
object RuleEngine {

    fun categoryFor(counterparty: String, rules: List<MerchantRule>, direction: Direction = Direction.Expense): String? =
        ruleFor(counterparty, rules, direction)?.categoryId

    fun ruleFor(counterparty: String, rules: List<MerchantRule>, direction: Direction = Direction.Expense): MerchantRule? {
        val key = counterparty.matchKey()
        return rules.firstOrNull { it.direction == direction && it.counterparty.matchKey() == key }
    }
}

/** The remembered merchant for [shop], on the key [RuleEngine] files by. */
fun List<RememberedMerchant>.forShop(shop: String): RememberedMerchant? {
    val key = shop.matchKey()
    return firstOrNull { it.counterparty.matchKey() == key }
}

private fun String.matchKey(): String = trim().lowercase()
