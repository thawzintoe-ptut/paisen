package com.ptut.expense.domain

import com.ptut.expense.locale.AppText

/** [share] is a whole percent of the period's spending, so the bar and the number agree. */
data class CategorySpend(
    val category: Category,
    val total: Satang,
    val share: Int,
    /** How many purchases make up [total]: "9 purchases" on the Sky row (#164). */
    val count: Int = 0,
    val subCategories: List<SubCategorySpend> = emptyList(),
)

data class SubCategorySpend(val name: String, val total: Satang)

/**
 * Income is excluded on purpose: a breakdown that mixes a salary in with the groceries
 * answers no question the reader actually has.
 *
 * Nothing is dropped (#115): spend under a category [categories] does not hold counts under one
 * [UnknownCategory] row, so the rows always add up to the period's total.
 */
fun List<Transaction>.spendByCategory(
    categories: List<Category>,
    subCategories: List<SubCategory> = emptyList(),
): List<CategorySpend> {
    val spent = filter { it.direction == Direction.Expense }
    val total = spent.map { it.amount }.sum()
    if (total == Satang.Zero) return emptyList()

    return spent
        .groupBy { transaction -> categories.firstOrNull { it.id == transaction.categoryId } ?: UnknownCategory }
        .map { (category, rows) ->
            val categoryTotal = rows.map { it.amount }.sum()
            CategorySpend(
                category = category,
                total = categoryTotal,
                // The Double is safe: it only ever becomes a truncated percent, and no money
                // is derived from it. Do not "fix" this into Satang arithmetic.
                share = ((categoryTotal.raw * 100.0) / total.raw).toInt(),
                count = rows.size,
                subCategories = rows.splitUnder(category.id, categoryTotal, subCategories),
            )
        }
        .sortedByDescending { it.total }
}

/**
 * [categoryId]'s spend by its own sub-categories, biggest first, then an unsorted row for the rest. Empty
 * when no row here has one, so a parent nobody splits reads exactly as before (#112).
 */
private fun List<Transaction>.splitUnder(
    categoryId: String,
    categoryTotal: Satang,
    subCategories: List<SubCategory>,
): List<SubCategorySpend> {
    val mine = subCategories.under(categoryId).associateBy { it.id }
    val filed = mapNotNull { row -> mine[row.subCategoryId]?.let { sub -> sub to row.amount } }
    if (filed.isEmpty()) return emptyList()

    val bySub = filed.groupBy({ it.first }, { it.second })
        .map { (sub, amounts) -> SubCategorySpend(sub.name, amounts.sum()) }
        .sortedByDescending { it.total }
    val rest = categoryTotal - filed.map { it.second }.sum()
    return if (rest == Satang.Zero) bySub else bySub + SubCategorySpend(AppText.copy.phrases.unsorted, rest)
}

/** Where spend goes when its category is not in the list a screen holds. */
val UnknownCategory: Category
    get() = Category(id = "unknown", name = AppText.copy.phrases.unknownCategory, icon = "more_horiz")
