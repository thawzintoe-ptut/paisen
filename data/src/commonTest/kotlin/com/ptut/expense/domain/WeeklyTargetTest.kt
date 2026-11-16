package com.ptut.expense.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeeklyTargetTest {

    /** A Wednesday. This week began Mon 16 Nov; last week was 9 to 15 Nov. */
    private val wednesday = LocalDate(2026, 11, 18)
    private val food = WeeklyTarget("food", Satang.ofMinorUnits(250_000))

    private var next = 0

    private fun spend(month: Int, day: Int, baht: Long, category: String = "food", direction: Direction = Direction.Expense) =
        Transaction(
            id = "t${next++}",
            occurredAtEpochMillis = LocalDateTime(2026, month, day, 12, 0).toInstant(BangkokTime).toEpochMilliseconds(),
            amount = Satang.ofMinorUnits(baht * 100),
            direction = direction,
            categoryId = category,
            counterparty = null,
        )

    @Test
    fun `this week counts only the target category since Monday`() {
        val rows = listOf(
            spend(11, 15, 900),
            spend(11, 16, 1_200),
            spend(11, 18, 700),
            spend(11, 17, 400, category = "transport"),
            spend(11, 17, 5_000, direction = Direction.Income),
        )

        val progress = rows.thisWeek(food, wednesday)

        assertEquals(Satang.ofMinorUnits(190_000), progress.spent)
        assertEquals(Satang.ofMinorUnits(-60_000), progress.over)
        assertEquals(0.76f, progress.fraction)
    }

    @Test
    fun `going past the target fills the bar and stops there`() {
        val progress = listOf(spend(11, 16, 2_900)).thisWeek(food, wednesday)

        assertEquals(Satang.ofMinorUnits(40_000), progress.over)
        assertEquals(1f, progress.fraction)
    }

    @Test
    fun `a week with nothing spent is under the target`() {
        val progress = emptyList<Transaction>().thisWeek(food, wednesday)

        assertEquals(Satang.Zero, progress.spent)
        assertEquals(0f, progress.fraction)
    }

    @Test
    fun `last week is the whole Monday to Sunday before this one`() {
        val rows = listOf(spend(11, 8, 1_000), spend(11, 9, 1_500), spend(11, 15, 1_000), spend(11, 16, 800))

        assertEquals(Satang.ofMinorUnits(250_000), rows.lastWeek(food, wednesday).spent)
    }

    @Test
    fun `the suggestion is a usual week rounded down to whole hundreds`() {
        val rows = listOf(
            spend(10, 27, 2_430),
            spend(11, 3, 2_150),
            spend(11, 10, 2_610),
            spend(11, 16, 9_000),
        )

        // Weeks of 26 Oct, 2 Nov and 9 Nov: a mean of ฿2,396.67, then ฿2,300.
        assertEquals(Satang.ofMinorUnits(230_000), rows.suggestedTarget("food", wednesday))
    }

    @Test
    fun `under two earlier weeks suggests nothing`() {
        assertNull(listOf(spend(11, 10, 2_000)).suggestedTarget("food", wednesday))
    }

    @Test
    fun `a category with nothing spent suggests nothing`() {
        val rows = listOf(spend(10, 27, 500, category = "transport"), spend(11, 10, 500, category = "transport"))

        assertNull(rows.suggestedTarget("food", wednesday))
    }
}
