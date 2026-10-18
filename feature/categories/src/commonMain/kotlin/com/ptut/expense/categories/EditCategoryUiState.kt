package com.ptut.expense.categories

import com.ptut.expense.domain.DEFAULT_COLOR
import com.ptut.expense.domain.Direction
/**
 * Screen 12a. The order of the fields is the order of how often each is used — icon, name,
 * sub-categories, delete — and the state is laid out the same way so the view cannot drift.
 */
data class EditCategoryUiState(
    val name: String = "",
    val icon: String = "",
    /**
     * Which kind of money this category may be filed under. Changing it offers different chips
     * next time and rewrites nothing already recorded — a transaction carries its own direction.
     */
    val direction: Direction = Direction.Expense,
    /** A [com.ptut.expense.design.CategoryTint] name, resolved to a fill by the design layer. */
    val color: String = DEFAULT_COLOR,
    val subCategories: List<SubCategoryRowUiModel> = emptyList(),
    /** Save is inert until something changes, so the button doubles as the dirty indicator. */
    val dirty: Boolean = false,
    /**
     * The reader changed something here (#214). Unlike [dirty] it is false on an untouched new
     * category, so closing one does not ask about changes nobody made.
     */
    val edited: Boolean = false,
    /**
     * False until the category has been read, and the screen draws no body while it is.
     *
     * [icon] is a glyph name and PtutIcon has no fallback on purpose, so the empty name standing
     * for "not read yet" is not something the tile can draw — it throws. This is also the state
     * for one frame after a delete, when the row the screen was opened for is gone.
     */
    val loaded: Boolean = false,
    /** FR-18: stated before the tap, never after it. */
    val deleteConsequence: String = "",
    /** Somewhere for the transactions to go. A category cannot be deleted without one. */
    val deleteDestinations: List<CategoryChoiceUiModel> = emptyList(),
    val canDelete: Boolean = false,
)

data class SubCategoryRowUiModel(
    val id: String,
    val name: String,
    /**
     * How many purchases it holds, all time (p20): the honest test of whether it earns its place.
     * One still at 0 is one the reader invented and never uses, and seeing that prompts them to
     * remove it.
     */
    val purchases: String,
)

/** A category the deleted one's rows can go to, with what it wears so the sheet draws it as itself (#179). */
data class CategoryChoiceUiModel(val id: String, val name: String, val icon: String, val color: String)
