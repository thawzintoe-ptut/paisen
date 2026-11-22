package com.ptut.expense.domain.categories

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Transaction

/** How many category chips a screen shows before More. */
const val SHOWN_CATEGORY_CHIPS = 3

/** The chips a screen draws, and how many categories wait behind More. */
data class CategoryChips(val shown: List<Category>, val moreCount: Int) {
    val hasMore: Boolean get() = moreCount > 0
}

/**
 * Hick's law: the suggested or chosen category first, then the most used ones, up to [limit].
 * A list that fits is drawn whole in its own order, so nothing jumps on a short list.
 */
fun List<Category>.chips(first: Category?, usage: Map<String, Int>, limit: Int = SHOWN_CATEGORY_CHIPS): CategoryChips {
    if (size <= limit) return CategoryChips(this, 0)
    val lead = listOfNotNull(first?.let { chosen -> firstOrNull { it.id == chosen.id } })
    val rest = filterNot { it.id == lead.firstOrNull()?.id }.sortedByDescending { usage[it.id] ?: 0 }.take(limit - lead.size)
    return CategoryChips(lead + rest, size - limit)
}

/**
 * The chips in the order the row already showed them, as [order] ids, so a tap never moves a chip
 * under the finger. A chosen category not shown takes the last place. Null [order] keeps these chips.
 */
fun CategoryChips.keepingOrder(order: List<String>?, all: List<Category>, selectedId: String?): CategoryChips {
    if (order == null || !hasMore) return this
    val kept = order.mapNotNull { id -> all.firstOrNull { it.id == id } }
    if (kept.size != shown.size) return this
    val selected = selectedId?.let { id -> all.firstOrNull { it.id == id } }
    if (selected == null || kept.any { it.id == selected.id }) return copy(shown = kept)
    return copy(shown = kept.dropLast(1) + selected)
}

/** A pick from the More sheet never takes the choice off: picking the chosen row only closes it. */
fun sheetPickChanges(picked: Category, selectedId: String?): Boolean = picked.id != selectedId

/** How often each category was filed, over the rows given. */
fun List<Transaction>.categoryUsage(): Map<String, Int> = mapNotNull { it.categoryId }.groupingBy { it }.eachCount()
