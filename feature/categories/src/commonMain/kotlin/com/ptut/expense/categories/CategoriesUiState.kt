package com.ptut.expense.categories

/**
 * FR-12 and FR-13. The screen is a settings list and a read of where the money went at once, so
 * a row carries this month's spend beside the name rather than only the name.
 */
data class CategoriesUiState(
    val rows: List<CategoryRowUiModel> = emptyList(),
    val loaded: Boolean = false,
)

data class CategoryRowUiModel(
    val id: String,
    val name: String,
    val icon: String,
    /** A [com.ptut.expense.design.CategoryTint] name. Stays a String, like [icon]. */
    val color: String,
    /** This month only. A lifetime figure would say nothing about what the reader buys now. */
    val spentThisMonth: String,
    /**
     * The row's second line, as the design writes it: how many finer labels, then what the
     * category cost. It reads "No sub-categories yet" rather than "0", because a zero there
     * looks like a count that failed rather than a choice nobody has made.
     */
    val meta: String,
    /** Chips, in the order the reader will read them. Empty for a category with no finer labels. */
    val subCategories: List<String>,
    /**
     * Whether this row has anywhere to go. The ends of the list carry a disabled control rather
     * than none, so the pair of arrows does not shift under the thumb between rows.
     */
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
)
