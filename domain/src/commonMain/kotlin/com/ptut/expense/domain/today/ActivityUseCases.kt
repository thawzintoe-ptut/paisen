package com.ptut.expense.domain.today

import com.ptut.expense.domain.DateRange
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.ReportPeriod
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.Trend
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.inside
import com.ptut.expense.domain.isAutomatic
import com.ptut.expense.domain.rangeContaining
import com.ptut.expense.domain.startOfDayEpochMillis
import com.ptut.expense.domain.summarise
import com.ptut.expense.domain.trendBetween
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

/** #127: one finished week's spending beside the week before it. */
data class WeekWrap(
    val week: DateRange,
    val spent: Satang,
    val trend: Trend,
    /** The last moment of its Sunday, so the wrap sits after the week it sums up. */
    val endsAtEpochMillis: Long,
)

/**
 * One wrap per finished Mon–Sun week on Bangkok days, from the first row's week to last week,
 * never this week (#127). The figure is Reports' week total, spending only.
 */
class GetWeeklyWrapsUseCase(private val homeToday: GetHomeTodayUseCase = GetHomeTodayUseCase()) {
    operator fun invoke(rows: List<Transaction>): List<WeekWrap> {
        val first = rows.minOfOrNull { it.occurredAtEpochMillis } ?: return emptyList()
        val thisWeek = ReportPeriod.Week.rangeContaining(homeToday())
        var week = ReportPeriod.Week.rangeContaining(first.dateIn(HomeTime))
        val wraps = mutableListOf<WeekWrap>()
        while (week.start < thisWeek.start) {
            val spent = rows.spentIn(week)
            wraps += WeekWrap(
                week = week,
                spent = spent,
                trend = trendBetween(previous = rows.spentIn(week.shiftedWeeks(-1)), current = spent),
                endsAtEpochMillis = week.endInclusive.plus(1, DateTimeUnit.DAY).startOfDayEpochMillis() - 1,
            )
            week = week.shiftedWeeks(1)
        }
        return wraps
    }

    private fun List<Transaction>.spentIn(week: DateRange): Satang = inside(week).summarise().expense

    private fun DateRange.shiftedWeeks(weeks: Int) =
        DateRange(start.plus(weeks, DateTimeUnit.WEEK), endInclusive.plus(weeks, DateTimeUnit.WEEK))
}

/** p23 names the shop when a run filed one slip and queued none: the one row filed while it ran (#127). */
class FindRunOnlyShopUseCase {
    operator fun invoke(run: ImportRun, rows: List<Transaction>): Transaction? {
        if (run.filed != 1 || run.queued != 0) return null
        val end = run.finishedAtEpochMillis ?: return null
        return rows.singleOrNull { row -> row.source.isAutomatic && row.filedAt?.let { it in run.startedAtEpochMillis..end } == true }
    }
}
