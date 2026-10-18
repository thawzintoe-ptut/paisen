package com.ptut.expense.domain

import com.ptut.expense.locale.AppText

/**
 * [icon] is a Material Symbols name, resolved to a glyph in the UI layer. [color] is a
 * [com.ptut.expense.design.CategoryTint] name, resolved the same way — a String and not the enum,
 * because the domain does not import the design layer.
 *
 * [direction] is which kind of money this category may be filed under, and it belongs on the row
 * rather than in two lists in this file: the categories are the reader's now, so a category they
 * made has to be able to say what it is for. Nothing derives a transaction's direction from here —
 * a row carries its own — so changing this offers different chips next time and rewrites nothing.
 */
data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val direction: Direction = Direction.Expense,
    val color: String = DEFAULT_COLOR,
    /**
     * Where this sits in the reader's own order. Ties break on name, so the seed keeps the order
     * the design drew it in until someone moves a row.
     */
    val sortOrder: Int = 0,
) {
    companion object {
        /**
         * What the schema installs on a fresh database, and the fallback a screen falls back to
         * before the table has answered. A screen offering a choice wants [forDirection] instead.
         *
         * Ids are stable and never derived from the name, so renaming a category cannot
         * orphan the shop rules pointing at it (FR-12).
         */
        fun seed(): List<Category> = spending + earning

        /**
         * Money out. C-2: a slip exists for money sent, so the confirm screen only ever offers
         * these and a rule can never point at anything else.
         */
        val spending: List<Category> = listOf(
            Category("food", "Food & drink", "restaurant", sortOrder = 0),
            Category("groceries", "Groceries", "local_grocery_store", sortOrder = 1),
            Category("transport", "Transport", "directions_bus", sortOrder = 2),
            Category("bills", "Bills", "receipt_long", sortOrder = 3),
            Category("shopping", "Shopping", "shopping_bag", sortOrder = 4),
            Category("other", "Other", "more_horiz", sortOrder = 5),
        )

        /**
         * Money in. One on a fresh install, and named for the direction rather than for a source
         * of it, because the design's own history row reads "Income · added by hand".
         *
         * A second one is now a thing the reader can make rather than a change to this list.
         */
        val earning: List<Category> = listOf(
            Category("income", "Income", "payments", Direction.Income, sortOrder = 6),
        )
    }
}

/**
 * The chips one kind of money may be filed under. FR-14 wants a category on both kinds, and
 * offering every one would let a salary be filed as Groceries and a coffee as Income.
 *
 * An extension over the list the reader actually holds, not a lookup in a compiled-in pair of
 * lists — that pair could not see a category they made, so a new one reached the history screens
 * and never the sheet that files into it.
 */
fun List<Category>.forDirection(direction: Direction): List<Category> =
    filter { it.direction == direction }

/**
 * An installed category the reader never renamed, named in the app's language. A name they gave
 * stays theirs. Only the screens see this: the table keeps the installed English name.
 */
fun Category.inAppLanguage(): Category {
    val installed = Category.seed().firstOrNull { it.id == id } ?: return this
    if (name != installed.name) return this
    return AppText.copy.categories.seedNames[id]?.let { copy(name = it) } ?: this
}

/** The other way, before a save: a translated installed name goes back into the table as the English one. */
fun Category.inTableLanguage(): Category {
    val installed = Category.seed().firstOrNull { it.id == id } ?: return this
    return if (name == AppText.copy.categories.seedNames[id]) copy(name = installed.name) else this
}
