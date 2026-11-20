package com.ptut.expense.domain.today

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.CategorySpend
import com.ptut.expense.domain.DateRange
import com.ptut.expense.domain.PeriodSummary
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReportPeriod
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.TargetProgress
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.UsualDay
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.inside
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.rangeContaining
import com.ptut.expense.domain.reports.SpendNames
import com.ptut.expense.domain.savedOn
import com.ptut.expense.domain.spendByCategory
import com.ptut.expense.domain.summarise
import com.ptut.expense.domain.thisWeek
import com.ptut.expense.domain.usualDay
import kotlinx.datetime.LocalDate

/** Home's three spans (#157, p05). The ranges are Reports' own, on Asia/Bangkok. */
enum class HomePeriod { Today, Week, Month }

/** #21: one week's spending in the target's category beside the target. */
data class TargetWeek(val categoryId: String, val progress: TargetProgress)

/** The slips waiting in To check, counted by why each one waits. */
data class ReviewCounts(val total: Int, private val byReason: Map<ReviewReason, Int>) {
    fun of(reason: ReviewReason): Int = byReason[reason] ?: 0
}

/** Home's chosen period, summed, before any of it is worded. */
data class HomeSummary(
    val range: DateRange,
    /** The period's rows, newest first. */
    val transactions: List<Transaction>,
    /** The period's money: the amount, and Income and Net beside it (#146, K1). */
    val summary: PeriodSummary,
    /** Rows saved today that carry another day's date, newest save first. Shown and never counted. */
    val savedToday: List<Transaction>,
    /** #119: today beside a usual one of its weekday, on the Today tab only; null with too little history. */
    val usualDay: UsualDay?,
    /** The period's spending by category, biggest first. */
    val byCategory: List<CategorySpend>,
    /** #21: this week beside the weekly target, on the Week tab only. */
    val target: TargetWeek?,
    /** Null when nothing waits. */
    val review: ReviewCounts?,
    /** The whole ledger as Home reads it, for the cards under the tabs. */
    val history: List<Transaction>,
)

/**
 * Home's period (#146, #157). Today reads today's own query; the week and month come off the whole
 * ledger, which can answer after it.
 */
class GetHomeSummaryUseCase {
    /** Null until today's query answers. */
    operator fun invoke(
        todays: LedgerSnapshot,
        ledger: LedgerSnapshot,
        period: HomePeriod,
        today: LocalDate,
        queued: List<QueuedSlip>,
        categories: List<Category>,
        target: WeeklyTarget?,
        names: SpendNames,
    ): HomeSummary? {
        val todaysRows = when (todays) {
            LedgerSnapshot.Unanswered -> return null
            is LedgerSnapshot.Rows -> todays.transactions
        }
        // Home is worth drawing before the whole ledger answers, and until then it supports nothing saved.
        val history = when (ledger) {
            LedgerSnapshot.Unanswered -> emptyList()
            is LedgerSnapshot.Rows -> ledger.transactions
        }
        val range = period.rangeContaining(today)
        val transactions = if (period == HomePeriod.Today) {
            todaysRows
        } else {
            history.inside(range).sortedByDescending { it.occurredAtEpochMillis }
        }
        return HomeSummary(
            range = range,
            transactions = transactions,
            summary = transactions.summarise(),
            savedToday = history.savedOn(today),
            usualDay = if (period == HomePeriod.Today) history.usualDay(today) else null,
            byCategory = transactions.spendByCategory(categories, names.unknown, names.unsorted),
            target = target?.takeIf { period == HomePeriod.Week }?.let { TargetWeek(it.categoryId, history.thisWeek(it, today)) },
            review = queued.takeIf { it.isNotEmpty() }?.let { slips -> ReviewCounts(slips.size, slips.groupingBy { it.reason }.eachCount()) },
            history = history,
        )
    }
}

private fun HomePeriod.rangeContaining(today: LocalDate): DateRange = when (this) {
    HomePeriod.Today -> DateRange(today, today)
    HomePeriod.Week -> ReportPeriod.Week.rangeContaining(today)
    HomePeriod.Month -> ReportPeriod.Month.rangeContaining(today)
}
