package com.ptut.expense.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeeklyCheckInTest {

    /** A Monday. Last week is Mon 9 to Sun 15 Nov; the four before start 12, 19, 26 Oct and 2 Nov. */
    private val monday = LocalDate(2026, 11, 16)

    private var next = 0

    private fun spend(day: LocalDate, baht: Long, category: String? = "food", shop: String? = null) = Transaction(
        id = "t${next++}",
        occurredAtEpochMillis = LocalDateTime(day.year, day.month, day.day, 12, 0).toInstant(BangkokTime).toEpochMilliseconds(),
        amount = Satang.ofMinorUnits(baht * 100),
        direction = Direction.Expense,
        categoryId = category,
        counterparty = shop,
    )

    private fun day(month: Int, day: Int) = LocalDate(2026, month, day)

    @Test
    fun `a week with no spending gives no check-in`() {
        val rows = listOf(spend(day(11, 2), 500))

        assertNull(rows.weeklyCheckIn(monday))
    }

    @Test
    fun `income alone is not spending`() {
        val rows = listOf(spend(day(11, 10), 500).copy(direction = Direction.Income))

        assertNull(rows.weeklyCheckIn(monday))
    }

    @Test
    fun `a ledger younger than two earlier weeks says only the total`() {
        val rows = listOf(spend(day(11, 3), 100), spend(day(11, 10), 400), spend(day(11, 15), 100))

        val check = rows.weeklyCheckIn(monday)!!

        assertEquals(day(11, 9), check.from)
        assertEquals(day(11, 15), check.to)
        assertEquals(Satang.ofMinorUnits(50_000), check.spent)
        assertNull(check.usual)
        assertNull(check.rise)
    }

    @Test
    fun `the usual week is the mean of the earlier weeks the ledger covers`() {
        val rows = listOf(
            spend(day(10, 27), 300),
            spend(day(11, 4), 500),
            spend(day(11, 12), 600),
        )

        val check = rows.weeklyCheckIn(monday)!!

        // Weeks of 26 Oct and 2 Nov only: the ledger starts in the week of 26 Oct.
        assertEquals(Satang.ofMinorUnits(40_000), check.usual)
        assertEquals(Satang.ofMinorUnits(20_000), check.difference)
    }

    @Test
    fun `the next step names the category that rose most and its top two shops`() {
        val rows = listOf(
            spend(day(10, 27), 200, "food"),
            spend(day(10, 28), 100, "transport"),
            spend(day(11, 4), 200, "food"),
            spend(day(11, 5), 100, "transport"),
            spend(day(11, 10), 500, "food", shop = "LAWSON"),
            spend(day(11, 11), 300, "food", shop = "7-ELEVEN"),
            spend(day(11, 12), 50, "food", shop = "CAFE AMAZON"),
            spend(day(11, 13), 150, "transport", shop = "BTS"),
        )

        val rise = rows.weeklyCheckIn(monday)!!.rise!!

        assertEquals("food", rise.categoryId)
        assertEquals(Satang.ofMinorUnits(65_000), rise.difference)
        assertEquals(listOf("LAWSON", "7-ELEVEN"), rise.shops)
    }

    @Test
    fun `a planned payment is never part of a rise and is counted beside it`() {
        val rows = listOf(
            spend(day(10, 27), 200, "food"),
            spend(day(11, 4), 200, "food"),
            spend(day(11, 10), 300, "food", shop = "LAWSON"),
            spend(day(11, 11), 600, "food", shop = "LAWSON").copy(planned = true),
        )

        val rise = rows.weeklyCheckIn(monday)!!.rise!!

        assertEquals(Satang.ofMinorUnits(10_000), rise.difference)
        assertEquals(Satang.ofMinorUnits(60_000), rise.planned)
    }

    @Test
    fun `a rise made only of planned payments is no rise`() {
        val rows = listOf(
            spend(day(10, 27), 200, "food"),
            spend(day(11, 4), 200, "food"),
            spend(day(11, 10), 900, "food").copy(planned = true),
        )

        assertNull(rows.weeklyCheckIn(monday)!!.rise)
    }

    @Test
    fun `a week below usual everywhere has no next step`() {
        val rows = listOf(
            spend(day(10, 27), 500),
            spend(day(11, 4), 500),
            spend(day(11, 12), 100),
        )

        val check = rows.weeklyCheckIn(monday)!!

        assertEquals(Satang.ofMinorUnits(-40_000), check.difference)
        assertNull(check.rise)
    }

    @Test
    fun `rows with no category never lead the next step`() {
        val rows = listOf(
            spend(day(10, 27), 100, category = null),
            spend(day(11, 4), 100, category = null),
            spend(day(11, 12), 900, category = null),
        )

        assertNull(rows.weeklyCheckIn(monday)!!.rise)
    }

    @Test
    fun `any day of the week looks back at the same last week`() {
        val rows = listOf(spend(day(11, 3), 100), spend(day(11, 10), 400))

        assertEquals(day(11, 9), rows.weeklyCheckIn(LocalDate(2026, 11, 22))!!.from)
    }
}
