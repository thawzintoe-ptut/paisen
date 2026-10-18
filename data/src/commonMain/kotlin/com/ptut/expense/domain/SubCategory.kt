package com.ptut.expense.domain

/**
 * FR-13. One level under a [Category] and never two — see [com.ptut.expense.data.local.entity.SubCategoryEntity]
 * for why the schema cannot express a third.
 *
 * [id] is stable and never derived from [name], for the reason a category's is: a rename must
 * reach history without orphaning the rows already filed under it.
 */
data class SubCategory(
    val id: String,
    val categoryId: String,
    val name: String,
)

/** [categoryId]'s own sub-categories, in the order they are held. None for no category. */
fun List<SubCategory>.under(categoryId: String?): List<SubCategory> =
    if (categoryId == null) emptyList() else filter { it.categoryId == categoryId }
