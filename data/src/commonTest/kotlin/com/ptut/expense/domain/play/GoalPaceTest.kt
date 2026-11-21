package com.ptut.expense.domain.play

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalPaceTest {

    private val today = LocalDate(2026, 11, 28)
    private val goal = Goal("g1", "Trip", Satang(1_000_000), "THB", start = LocalDate(2026, 11, 1), createdAtEpochMillis = 0)

    private fun counted(day: LocalDate, usual: Long) = GoalDay(goal.id, day, Satang(usual), atEpochMillis = 0)

    @Test
    fun `a counted day keeps its usual less what it spent`() {
        val rows = listOf(row("lunch", LocalDate(2026, 11, 22), 8_000))

        val pace = rows.goalPace(goal, listOf(counted(LocalDate(2026, 11, 22), 30_000)), today, BangkokTime)

        assertEquals(Satang(22_000), pace.saved)
        assertTrue(pace.hasDays)
    }

    @Test
    fun `a buy later that day shrinks the count and never below empty`() {
        val day = LocalDate(2026, 11, 22)
        val days = listOf(counted(day, 30_000))
        val evening = listOf(row("lunch", day, 8_000), row("dinner", day, 20_000))
        val spree = evening + row("shoes", day, 90_000)

        assertEquals(Satang(2_000), evening.goalPace(goal, days, today, BangkokTime).saved)
        assertEquals(Satang.Zero, spree.goalPace(goal, days, today, BangkokTime).saved)
    }

    @Test
    fun `not today and another jar's days count nothing`() {
        val days = listOf(
            GoalDay(goal.id, LocalDate(2026, 11, 22), usual = null, atEpochMillis = 0),
            GoalDay("other", LocalDate(2026, 11, 23), Satang(30_000), atEpochMillis = 0),
        )

        val pace = emptyList<Transaction>().goalPace(goal, days, today, BangkokTime)

        assertFalse(pace.hasDays)
        assertEquals(Satang.Zero, pace.saved)
        assertNull(pace.reachOn)
    }

    @Test
    fun `the last four weeks of counted days set the pace and skipping a habit adds its spending`() {
        val days = listOf(counted(LocalDate(2026, 11, 22), 50_000), counted(LocalDate(2026, 11, 23), 50_000))
        val rows = listOf(row("taxi", LocalDate(2026, 11, 12), 80_000, category = "transport"))

        val pace = rows.goalPace(goal, days, today, BangkokTime)
        val skipped = rows.goalPaceWithout(goal, days, "transport", today, BangkokTime)

        assertEquals(Satang(100_000), pace.saved)
        assertEquals(Satang(25_000), pace.perWeek)
        assertEquals(LocalDate(2027, 8, 7), pace.reachOn)
        assertEquals(Satang(45_000), skipped.perWeek)
    }

    @Test
    fun `a pace too slow to plan for names no date and never crashes`() {
        val big = goal.copy(target = Satang(100_000_000_000))

        assertNull(emptyList<Transaction>().goalPace(big, listOf(GoalDay(big.id, today, Satang(4), 0)), today, BangkokTime).reachOn)
    }
}
