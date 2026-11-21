package com.ptut.expense.domain.play

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class GoalPaceTest {

    private val today = LocalDate(2026, 11, 28)
    private val goal = Goal("g1", "Trip", Satang(1_000_000), "THB", start = LocalDate(2026, 11, 1), createdAtEpochMillis = 0)

    /** 4,000 in, 2,000 out in the last four weeks: 500 a week kept. */
    private val month = listOf(
        row("in", LocalDate(2026, 11, 2), 400_000, category = "income", direction = Direction.Income),
        row("food", LocalDate(2026, 11, 10), 120_000),
        row("taxi", LocalDate(2026, 11, 12), 80_000, category = "transport"),
    )

    @Test
    fun `the jar holds what was kept since it began and fills at the recent pace`() {
        val pace = month.goalPace(goal, today, BangkokTime)

        assertEquals(Satang(200_000), pace.saved)
        assertEquals(Satang(50_000), pace.perWeek)
        assertEquals(LocalDate(2027, 3, 20), pace.reachOn)
    }

    @Test
    fun `skipping a habit moves the date closer`() {
        val pace = month.goalPaceWithout(goal, "transport", today, BangkokTime)

        assertEquals(Satang(200_000), pace.saved)
        assertEquals(Satang(70_000), pace.perWeek)
    }

    @Test
    fun `with no income the jar says so and shows no date`() {
        val pace = listOf(row("food", LocalDate(2026, 11, 10), 120_000)).goalPace(goal, today, BangkokTime)

        assertFalse(pace.hasIncome)
        assertNull(pace.reachOn)
        assertEquals(Satang.Zero, pace.saved)
    }

    @Test
    fun `a pace too slow to plan for names no date and never crashes`() {
        val big = goal.copy(target = Satang(100_000_000_000))
        val crumbs = listOf(row("in", LocalDate(2026, 11, 20), 4, category = "income", direction = Direction.Income))

        assertNull(crumbs.goalPace(big, today, BangkokTime).reachOn)
    }
}
