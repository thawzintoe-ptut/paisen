package com.ptut.expense.domain.categories

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ReportPeriod
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.inside
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.rangeContaining
import com.ptut.expense.domain.suggestedTarget
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** FR-12: the reader's categories as stored, as they change. */
class ObserveCategoriesUseCase(private val categories: CategoryRepository) {
    operator fun invoke(): StateFlow<List<Category>> = categories.categories
}

/** FR-13: every sub-category as stored, as they change. */
class ObserveSubCategoriesUseCase(private val categories: CategoryRepository) {
    operator fun invoke(): StateFlow<List<SubCategory>> = categories.subCategories
}

/** What the Categories row on Settings counts (#168). */
data class CategoryCounts(val categories: Int = 0, val subCategories: Int = 0)

/** #168: how many categories and sub-categories there are; none in a graph with no category table. */
class ObserveCategoryCountsUseCase(private val categories: CategoryRepository? = null) {
    operator fun invoke(): Flow<CategoryCounts> =
        categories?.let { repository ->
            repository.categories.combine(repository.subCategories) { all, subs -> CategoryCounts(all.size, subs.size) }
        } ?: flowOf(CategoryCounts())
}

/** One category on the Categories screen: its sub-categories and what it cost this month. */
data class CategorySpend(val category: Category, val subCategories: List<SubCategory>, val spentThisMonth: Satang)

/**
 * FR-19, US-9: each category in [readersOrder] with this home month's spending to today, read from the rows and never
 * stored so no cached total outlives an edit (FR-18). Null while the ledger has not answered, which is not an empty month.
 */
class ObserveMonthlySpendByCategoryUseCase(
    private val ledger: LedgerRepository,
    private val categories: CategoryRepository,
    private val homeToday: GetHomeTodayUseCase = GetHomeTodayUseCase(),
) {
    operator fun invoke(): Flow<List<CategorySpend>?> =
        combine(ledger.allTransactions, categories.categories, categories.subCategories) { snapshot, known, subs ->
            when (snapshot) {
                LedgerSnapshot.Unanswered -> null
                is LedgerSnapshot.Rows -> spendByCategory(snapshot.transactions, known, subs)
            }
        }

    private fun spendByCategory(transactions: List<Transaction>, known: List<Category>, subs: List<SubCategory>): List<CategorySpend> {
        val thisMonth = transactions.inside(ReportPeriod.Month.rangeContaining(homeToday()))
        val byCategory = subs.groupBy { it.categoryId }
        return known.sortedWith(readersOrder).map { category ->
            CategorySpend(category, byCategory[category.id].orEmpty(), thisMonth.spentUnder(category.id))
        }
    }

    /**
     * Spending only. An income category's rows are money in, and adding them to a "spent" figure
     * would make the one category that earns look like the one that costs most.
     */
    private fun List<Transaction>.spentUnder(categoryId: String): Satang = filter {
        it.categoryId == categoryId && it.direction == Direction.Expense
    }.fold(Satang.Zero) { running, row -> running + row.amount }
}

/** What Edit category reads off the rows: purchases per sub-category, and a usual week to suggest (#21). */
data class CategoryStats(val purchasesBySubCategory: Map<String, Int>, val suggestedTarget: Satang?)

/** All time, not this month: a count is what a sub-category holds (p20). */
class GetCategoryStatsUseCase(private val homeToday: GetHomeTodayUseCase = GetHomeTodayUseCase()) {
    operator fun invoke(snapshot: LedgerSnapshot, categoryId: String): CategoryStats {
        val rows = when (snapshot) {
            LedgerSnapshot.Unanswered -> emptyList()
            is LedgerSnapshot.Rows -> snapshot.transactions
        }
        return CategoryStats(
            purchasesBySubCategory = rows.mapNotNull { it.subCategoryId }.groupingBy { it }.eachCount(),
            suggestedTarget = rows.suggestedTarget(categoryId, homeToday()),
        )
    }
}

/** FR-18, #219: how many rows and shop rules deleting a category would move. */
data class DeleteCounts(val transactions: Int, val shopRules: Int)

/** FR-18, #219: what a delete would move, stated before the reader is asked where it goes. */
class GetDeleteOptionsUseCase(private val categories: CategoryRepository) {
    suspend operator fun invoke(categoryId: String): DeleteCounts =
        DeleteCounts(categories.transactionCount(categoryId), categories.shopRuleCount(categoryId))
}
