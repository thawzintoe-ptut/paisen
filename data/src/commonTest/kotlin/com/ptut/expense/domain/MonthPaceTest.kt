package com.ptut.expense.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MonthPaceTest {

    private val today = LocalDate(2026, 8, 14)

    @Test
    fun theMonthSoFarCountsSpendingOnlyUpToToday() {
        val rows = listOf(
            expense(month = 8, day = 1, satang = 40_000),
            expense(month = 8, day = 14, satang = 10_000),
            income(month = 8, day = 3, satang = 3_000_000),
            expense(month = 8, day = 15, satang = 99_000),
        )

        assertEquals(Satang(50_000), rows.monthPace(today).spent)
    }

    @Test
    fun aLedgerThatBeganThisMonthHasNoUsual() {
        val rows = listOf(expense(month = 8, day = 2, satang = 40_000))

        assertNull(rows.monthPace(today).usual)
    }

    /** July was joined on the 10th, so its first nine days would read as a quiet month. */
    @Test
    fun aMonthJoinedHalfwayThroughIsNotUsual() {
        val rows = listOf(
            expense(month = 7, day = 10, satang = 40_000),
            expense(month = 8, day = 2, satang = 40_000),
        )

        assertNull(rows.monthPace(today).usual)
    }

    @Test
    fun oneWholeEarlierMonthIsCutAtTheSameDate() {
        val rows = listOf(
            expense(month = 7, day = 1, satang = 30_000),
            expense(month = 7, day = 14, satang = 20_000),
            expense(month = 7, day = 15, satang = 99_000),
            expense(month = 8, day = 2, satang = 40_000),
        )

        val pace = rows.monthPace(today)

        assertEquals(Satang(50_000), pace.usual)
        assertEquals(Satang(-10_000), pace.difference)
    }

    @Test
    fun usualIsTheMedianOfTheLastThreeMonthsSoOneBigMonthDoesNotSetIt() {
        val rows = listOf(
            expense(month = 4, day = 1, satang = 999_000),
            expense(month = 5, day = 1, satang = 30_000),
            expense(month = 6, day = 1, satang = 900_000),
            expense(month = 7, day = 1, satang = 50_000),
            expense(month = 8, day = 2, satang = 40_000),
        )

        assertEquals(Satang(50_000), rows.monthPace(today).usual)
    }

    /** 31 March has no 31 February, so February is read whole. */
    @Test
    fun aShorterEarlierMonthIsReadToItsLastDay() {
        val rows = listOf(
            expense(month = 2, day = 1, satang = 10_000),
            expense(month = 2, day = 28, satang = 5_000),
            expense(month = 3, day = 30, satang = 1_000),
        )

        assertEquals(Satang(15_000), rows.monthPace(LocalDate(2026, 3, 31)).usual)
    }

    private fun expense(month: Int, day: Int, satang: Long) = row(month, day, satang, Direction.Expense)

    private fun income(month: Int, day: Int, satang: Long) = row(month, day, satang, Direction.Income)

    private fun row(month: Int, day: Int, satang: Long, direction: Direction) = Transaction(
        id = "$direction$month$day$satang",
        occurredAtEpochMillis = LocalDateTime(2026, month, day, 12, 0)
            .toInstant(BangkokTime)
            .toEpochMilliseconds(),
        amount = Satang(satang),
        direction = direction,
        categoryId = "food",
        counterparty = "Somewhere",
    )
}
