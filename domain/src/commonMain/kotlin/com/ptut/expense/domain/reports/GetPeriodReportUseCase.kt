package com.ptut.expense.domain.reports

import com.ptut.expense.domain.BucketSpend
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.DateRange
import com.ptut.expense.domain.PeriodSummary
import com.ptut.expense.domain.ReportPeriod
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.Trend
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.averageBy
import com.ptut.expense.domain.bucketSize
import com.ptut.expense.domain.inside
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.needsWholeLedger
import com.ptut.expense.domain.previousRangeBefore
import com.ptut.expense.domain.rangeContaining
import com.ptut.expense.domain.spendPerBucket
import com.ptut.expense.domain.summarise
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import com.ptut.expense.domain.trendBetween
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn

/** One Reports period summed (#164, p14), before any of it is worded. */
data class PeriodReport(
    val period: ReportPeriod,
    val range: DateRange,
    /** The period's money; its expense is the total over the chart. */
    val summary: PeriodSummary,
    /** Spending against the period before; no percent when nothing was spent then. */
    val trend: Trend,
    /** Rows of any kind: a week of nothing but income still recorded something. */
    val hasTransactions: Boolean,
    /** The chart's bars, empty ones included. */
    val buckets: List<BucketSpend>,
    /** The bar today falls in. */
    val current: Int,
    val tallest: Satang,
    /** The dashed line, over the bars begun so far; null when nothing was spent. */
    val average: Satang?,
    val categories: List<CategoryComparison>,
)

/**
 * Reports' chosen period as the ledger changes. The long periods compare against a stretch the
 * recent window does not hold, so they read the whole ledger; the week and month read recent rows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetPeriodReportUseCase(
    private val ledger: LedgerRepository,
    private val homeToday: GetHomeTodayUseCase = GetHomeTodayUseCase(),
    private val compareCategorySpend: CompareCategorySpendUseCase = CompareCategorySpendUseCase(),
    /** A year of rows is summed on every change, so the work stays off the main thread. */
    private val work: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Null until the ledger answers. */
    operator fun invoke(
        period: Flow<ReportPeriod>,
        categories: Flow<List<Category>>,
        subCategories: Flow<List<SubCategory>>,
        target: Flow<WeeklyTarget?>,
        names: SpendNames,
    ): Flow<PeriodReport?> = period
        .flatMapLatest { chosen ->
            val rows = if (chosen.needsWholeLedger) ledger.allTransactions else ledger.recentTransactions
            combine(rows, categories, subCategories, target) { snapshot, filing, subs, weekly ->
                when (snapshot) {
                    LedgerSnapshot.Unanswered -> null
                    is LedgerSnapshot.Rows -> report(snapshot.transactions, chosen, filing, subs, weekly, names)
                }
            }
        }
        .flowOn(work)

    private fun report(
        transactions: List<Transaction>,
        chosen: ReportPeriod,
        categories: List<Category>,
        subCategories: List<SubCategory>,
        target: WeeklyTarget?,
        names: SpendNames,
    ): PeriodReport {
        val today = homeToday()
        val range = chosen.rangeContaining(today)
        val inRange = transactions.inside(range)
        val previousRows = transactions.inside(chosen.previousRangeBefore(today))
        val summary = inRange.summarise()
        val buckets = inRange.spendPerBucket(range, chosen.bucketSize)
        return PeriodReport(
            period = chosen,
            range = range,
            summary = summary,
            trend = trendBetween(previous = previousRows.summarise().expense, current = summary.expense),
            hasTransactions = inRange.isNotEmpty(),
            buckets = buckets,
            current = buckets.indexOfFirst { today in it }.coerceAtLeast(0),
            tallest = buckets.maxOfOrNull { it.spent } ?: Satang.Zero,
            average = buckets.averageBy(today)?.takeIf { it > Satang.Zero },
            categories = compareCategorySpend(inRange, previousRows, chosen, categories, subCategories, target, names),
        )
    }
}
