package com.ptut.expense.domain.reports

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.CategorySpend
import com.ptut.expense.domain.ReportPeriod
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.Trend
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.spendByCategory
import com.ptut.expense.domain.trendBetween

/** One category's spend this period beside the same category the period before (p14). */
data class CategoryComparison(
    val spend: CategorySpend,
    /** No percent when the category had no spending before: it cannot be a percentage of itself. */
    val trend: Trend,
    /** #21: the weekly target, on the week only and beside its own category only. */
    val target: Satang?,
)

/** Reports' breakdown: every category with spending in [current], biggest first, each against [previous]. */
class CompareCategorySpendUseCase {
    operator fun invoke(
        current: List<Transaction>,
        previous: List<Transaction>,
        period: ReportPeriod,
        categories: List<Category>,
        subCategories: List<SubCategory>,
        target: WeeklyTarget?,
        names: SpendNames,
    ): List<CategoryComparison> {
        val before = previous.spendByCategory(categories, names.unknown, names.unsorted).associate { it.category.id to it.total }
        val weekly = target?.takeIf { period == ReportPeriod.Week }
        return current.spendByCategory(categories, names.unknown, names.unsorted, subCategories).map { spend ->
            CategoryComparison(
                spend = spend,
                trend = trendBetween(previous = before[spend.category.id] ?: Satang.Zero, current = spend.total),
                target = weekly?.takeIf { it.categoryId == spend.category.id }?.amount,
            )
        }
    }
}
