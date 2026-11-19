package com.ptut.expense.domain

/** Who filed a row. A row never says "edited": that lives in detail and the Edited filter (#146, K7). */
enum class Provenance {
    Auto,
    ByHand,
}

/**
 * What a row's meta line joins, kept apart so a Sky row can put the time under the amount (#156).
 * [category] is null when it is already the row's name, so "Food & drink" is not said twice.
 * [foreign] is a kyat record's own figure, "K29,500" (#33); the row's amount stays the baht it counts as.
 */
data class MetaParts(val category: String?, val time: String?, val provenance: Provenance, val foreign: String? = null)

fun Transaction.category(categories: List<Category>): Category? =
    categories.firstOrNull { it.id == categoryId }

/**
 * Income carries the payments glyph rather than the category's own. Nothing in the spec asks
 * for it — FR-10 is about filing a known counterparty without asking — so it is a house rule.
 *
 * Here rather than in a screen for the same reason a row's display name is: the Ledger row and the
 * detail card draw the same transaction, and a second copy of this rule is how the two start
 * showing different pictures for one row.
 */
fun Transaction.iconName(categories: List<Category>): String = when (direction) {
    Direction.Income -> INCOME_ICON
    Direction.Expense -> category(categories)?.icon ?: UNCATEGORISED_ICON
}

/**
 * The tint the row's category wears, beside [iconName] for the same reason: one transaction
 * drawn on two screens has to come out the same colour on both.
 *
 * Unlike the glyph, income is not forced. A reader can hold several income categories now, and
 * telling salary from a refund is exactly what a colour is for.
 */
fun Transaction.colorName(categories: List<Category>): String =
    category(categories)?.color ?: DEFAULT_COLOR

/** The glyph for spending whose category names none, and for a category that has gone. */
private const val UNCATEGORISED_ICON = "more_horiz"

private const val INCOME_ICON = "payments"
