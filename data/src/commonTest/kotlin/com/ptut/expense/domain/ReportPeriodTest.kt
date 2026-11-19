package com.ptut.expense.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
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

    @Test
    fun insideKeepsTheRowsFromTheFirstToTheLastDayOfTheRange() {
        val week = ReportPeriod.Week.rangeContaining(friday)
        val rows = listOf(
            row("before", LocalDateTime(2026, 8, 9, 23, 59)),
            row("monday", LocalDateTime(2026, 8, 10, 0, 0)),
            row("sunday", LocalDateTime(2026, 8, 16, 23, 59)),
            row("after", LocalDateTime(2026, 8, 17, 0, 0)),
        )

        assertEquals(listOf("monday", "sunday"), rows.inside(week, BangkokTime).map { it.id })
    }

    private fun row(id: String, at: LocalDateTime) = Transaction(
        id = id,
        occurredAtEpochMillis = at.toInstant(BangkokTime).toEpochMilliseconds(),
        amount = Satang(1_000),
        direction = Direction.Expense,
        categoryId = null,
        counterparty = null,
    )
}
