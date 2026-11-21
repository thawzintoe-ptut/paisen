package com.ptut.expense.domain.play

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoachLineTest {

    /** A Friday, with four earlier Fridays behind it. */
    private val today = LocalDate(2026, 11, 27)

    private fun fridays(category: String, minor: Long) = (1..4).map { weeksAgo ->
        row("$category-$weeksAgo", category, minor, today.minus(weeksAgo, DateTimeUnit.WEEK))
    }

    @Test
    fun `an ordinary day says nothing`() {
        val rows = fridays("food", 20_000) + row("today", "food", 21_000, today)

        assertNull(rows.coachLine(today, BangkokTime))
    }

    @Test
    fun `the category furthest from usual is named with the rows behind it`() {
        val rows = fridays("food", 20_000) + fridays("transport", 10_000) +
            row("a", "food", 50_000, today) + row("b", "transport", 12_000, today)

        val line = rows.coachLine(today, BangkokTime)

        assertEquals(CoachLine("food", Satang(30_000), DayOfWeek.FRIDAY, listOf("a")), line)
    }

    @Test
    fun `a day well under usual is named too as less`() {
        val rows = fridays("food", 60_000) + row("a", "food", 10_000, today)

        assertEquals(Satang(-50_000), rows.coachLine(today, BangkokTime)?.difference)
    }

    @Test
    fun `a small gap in money is not called out however large in percent`() {
        val rows = fridays("food", 1_000) + row("a", "food", 9_000, today)

        assertNull(rows.coachLine(today, BangkokTime))
    }

    private fun row(id: String, category: String, minor: Long, day: LocalDate) = Transaction(
        id = id,
        occurredAtEpochMillis = LocalDateTime(day.year, day.month, day.day, 12, 0).toInstant(BangkokTime).toEpochMilliseconds(),
        amount = Satang(minor),
        direction = Direction.Expense,
        categoryId = category,
        counterparty = "Shop",
    )
}
