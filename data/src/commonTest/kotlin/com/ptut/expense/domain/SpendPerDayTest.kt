package com.ptut.expense.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class SpendPerDayTest {

    private val week = DateRange(LocalDate(2026, 8, 10), LocalDate(2026, 8, 16))

    /**
     * #11's acceptance line. The chart and the hero above it are the same rows read twice, so a
     * week whose bars do not add up to its total is the aggregation disagreeing with itself —
     * the thing the Ledger was built first to make checkable by eye.
     */
    @Test
    fun theDaysSumToTheSameFigureTheHeroCallsSpent() {
        val rows = listOf(
            expense(day = 10, satang = 40_000),
            expense(day = 12, satang = 125_000),
            expense(day = 14, satang = 7_050),
            expense(day = 14, satang = 99),
            income(day = 12, satang = 3_200_000),
        )

        assertEquals(
            rows.summarise().expense,
            rows.spendPerDay(week).map { it.spent }.sum(),
        )
    }

    /** Seven columns whatever happened. A week that dropped its quiet days would rescale. */
    @Test
    fun everyDayInTheRangeIsPresentEvenWithNothingOnIt() {
        val days = listOf(expense(day = 12, satang = 40_000)).spendPerDay(week)

        assertEquals(7, days.size)
        assertEquals(LocalDate(2026, 8, 10), days.first().date)
        assertEquals(LocalDate(2026, 8, 16), days.last().date)
        assertEquals(Satang.Zero, days.first().spent)
    }

    /** Income is not spending. A salary landing mid-week must not raise a bar. */
    @Test
    fun incomeNeverRaisesADayBar() {
        val days = listOf(income(day = 12, satang = 3_200_000)).spendPerDay(week)

        assertEquals(List(7) { Satang.Zero }, days.map { it.spent })
    }

    private fun expense(day: Int, satang: Long) = row(day, satang, Direction.Expense)

    private fun income(day: Int, satang: Long) = row(day, satang, Direction.Income)

    private fun row(day: Int, satang: Long, direction: Direction) = Transaction(
        id = "$direction$day$satang",
        occurredAtEpochMillis = LocalDateTime(2026, 8, day, 12, 0)
            .toInstant(BangkokTime)
            .toEpochMilliseconds(),
        amount = Satang(satang),
        direction = direction,
        categoryId = "food",
        counterparty = "Somewhere",
    )
}
