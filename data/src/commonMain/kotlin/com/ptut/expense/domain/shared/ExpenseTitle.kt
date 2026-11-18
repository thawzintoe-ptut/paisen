package com.ptut.expense.domain.shared

/**
 * A blank description is saved as the category's name, so the row has a title. When History changes
 * that category, [from] is the old name and [to] the new one, and the shared copy renames with it.
 */
data class TitleChange(val from: String, val to: String)

object ExpenseTitle {

    /** The note's first line when there is one, else the description, else the category's name. */
    fun shown(note: String?, description: String, categoryName: String?): String =
        firstLine(note) ?: description.trim().ifEmpty { categoryName.orEmpty() }

    /** [description] with the old category name swapped for the new one. A typed description stays as typed. */
    fun followed(description: String, change: TitleChange?): String =
        if (change != null && description.trim() == change.from) change.to else description

    private fun firstLine(note: String?): String? =
        note?.lineSequence()?.map(String::trim)?.firstOrNull(String::isNotEmpty)
}
