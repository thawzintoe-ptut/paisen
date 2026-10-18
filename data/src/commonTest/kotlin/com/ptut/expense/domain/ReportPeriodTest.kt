package com.ptut.expense.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportPeriodTest {

    private val friday = LocalDate(2026, 8, 14)

    @Test
    fun aWeekIsTheMondayToSundayThatTodayFallsIn() {
        val week = ReportPeriod.Week.rangeContaining(friday)

        assertEquals(LocalDate(2026, 8, 10), week.start)
        assertEquals(LocalDate(2026, 8, 16), week.endInclusive)
        assertEquals("Mon 10 – Sun 16", week.label())
    }

    @Test
    fun aMonthRunsFromTheFirstToTodayBecauseTheRestHasNotHappened() {
        val month = ReportPeriod.Month.rangeContaining(friday)

        assertEquals(LocalDate(2026, 8, 1), month.start)
        assertEquals(friday, month.endInclusive)
        assertEquals("1 – 14 Aug", month.label())
    }

    @Test
    fun theBaselineIsTheSameSpanOneWeekOrOneMonthBack() {
        assertEquals(
            LocalDate(2026, 8, 3)..LocalDate(2026, 8, 9),
            ReportPeriod.Week.previousRangeBefore(friday).let { it.start..it.endInclusive },
        )
        assertEquals(
            LocalDate(2026, 7, 1)..LocalDate(2026, 7, 14),
            ReportPeriod.Month.previousRangeBefore(friday).let { it.start..it.endInclusive },
        )
    }
}
