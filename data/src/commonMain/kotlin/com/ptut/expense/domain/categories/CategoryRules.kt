package com.ptut.expense.domain.categories

import com.ptut.expense.domain.Category

/**
 * The order the screen shows and a move counts against, so the two cannot drift. It mirrors `ORDER BY sortOrder, name`,
 * or a move lands one row away from where the reader aimed it.
 */
val readersOrder: Comparator<Category> = compareBy<Category> { it.sortOrder }.thenBy { it.name }

/**
 * #116: what NEW CATEGORY opens on, named [name]: last in the reader's order, so no category
 * they know moves. Nothing is stored until Save.
 */
fun draftCategory(id: String, known: List<Category>, name: String): Category {
    val last = known.maxOfOrNull { it.sortOrder } ?: -1
    return Category(id = id, name = name, icon = "label", sortOrder = last + 1)
}

/** FR-18: where [category]'s rows may go. Never itself, and never the other direction: a delete must not turn spending into income. */
fun List<Category>.deleteDestinationsFor(category: Category): List<Category> =
    filter { it.id != category.id && it.direction == category.direction }
