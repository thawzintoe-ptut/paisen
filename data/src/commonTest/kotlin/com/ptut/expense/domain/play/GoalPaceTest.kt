package com.ptut.expense.domain.play

import com.ptut.expense.domain.Satang
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalPaceTest {

    private val today = LocalDate(2026, 11, 28)
    private val goal = Goal("g1", "Trip", Satang(1_000_000), "THB", start = LocalDate(2026, 11, 1), createdAtEpochMillis = 0)

    private fun put(day: LocalDate, minor: Long, goalId: String = goal.id, id: String = "$goalId:$day:$minor") =
        GoalDeposit(id, goalId, Satang(minor), day, atEpochMillis = 0, source = DepositSource.Slip, account = "••4821")

    @Test
    fun `the jar holds exactly what went in`() {
        val pace = listOf(put(LocalDate(2026, 11, 22), 50_000), put(LocalDate(2026, 11, 25), 22_000)).goalPace(goal, today)

        assertEquals(Satang(72_000), pace.saved)
        assertTrue(pace.hasDeposits)
    }

    @Test
    fun `money in another jar and a deposit dated after today count nothing`() {
        val deposits = listOf(put(LocalDate(2026, 11, 22), 50_000, goalId = "other"), put(LocalDate(2026, 12, 1), 50_000))

        val pace = deposits.goalPace(goal, today)

        assertFalse(pace.hasDeposits)
        assertEquals(Satang.Zero, pace.saved)
        assertNull(pace.reachOn)
    }

    @Test
    fun `the last four weeks set the pace and the date`() {
        val deposits = listOf(put(LocalDate(2026, 11, 22), 50_000), put(LocalDate(2026, 11, 23), 50_000), put(LocalDate(2026, 10, 1), 50_000))

        val pace = deposits.goalPace(goal, today)

        assertEquals(Satang(150_000), pace.saved)
        assertEquals(Satang(25_000), pace.perWeek)
        assertEquals(LocalDate(2027, 7, 24), pace.reachOn)
    }

    @Test
    fun `a full jar is reached today and a pace that would take decades names no date`() {
        val big = goal.copy(target = Satang(Long.MAX_VALUE / 2))

        assertEquals(today, listOf(put(today, 1_000_000)).goalPace(goal, today).reachOn)
        assertNull(listOf(put(today, 4)).goalPace(big, today).reachOn)
    }

    @Test
    fun `this week counts from Monday`() {
        val deposits = listOf(put(LocalDate(2026, 11, 22), 50_000), put(LocalDate(2026, 11, 23), 22_000), put(LocalDate(2026, 11, 28), 10_000))

        assertEquals(Satang(32_000), deposits.savedThisWeek(goal, today))
    }

    @Test
    fun `put money in offers the leftover then the pace then a round amount each once and never zero`() {
        assertEquals(listOf(Satang(22_000), Satang(33_000), Satang(50_000)), depositChoices(Satang(22_000), Satang(33_000), minorPerWhole = 100))
        assertEquals(listOf(Satang(50_000)), depositChoices(Satang.Zero, null, minorPerWhole = 100))
        assertEquals(listOf(Satang(50_000)), depositChoices(Satang(50_000), Satang(50_000), minorPerWhole = 100))
    }
}
