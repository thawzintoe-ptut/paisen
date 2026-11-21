package com.ptut.expense.domain.play

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import kotlin.time.measureTime

class PlayRunTest {

    /** A Wednesday, so the week has days behind it and days to come. */
    private val today = LocalDate(2026, 11, 25)

    private fun day(dayOfMonth: Int, cleared: Boolean = true, answered: Boolean = false) = PlayDay(
        date = LocalDate(2026, 11, dayOfMonth),
        clearedAtEpochMillis = if (cleared) 1L else null,
        answeredAtEpochMillis = if (answered) 1L else null,
    )

    @Test
    fun `nothing played is an empty run with today waiting`() {
        val run = emptyList<PlayDay>().playRun(today)

        assertEquals(0, run.length)
        assertEquals(listOf(DayMark.Missed, DayMark.Missed, DayMark.Today, DayMark.Ahead, DayMark.Ahead, DayMark.Ahead, DayMark.Ahead), run.week)
    }

    @Test
    fun `the first cleared day starts the run`() {
        val run = listOf(day(25)).playRun(today)

        assertEquals(1, run.length)
        assertTrue(run.todayCleared)
    }

    @Test
    fun `an uncleared today keeps yesterday's run`() {
        val run = listOf(day(23), day(24)).playRun(today)

        assertEquals(2, run.length)
        assertFalse(run.todayCleared)
        assertEquals(DayMark.Today, run.week[2])
    }

    @Test
    fun `a gap with no freeze ends the run quietly`() {
        val run = listOf(day(21), day(22), day(24)).playRun(today)

        assertEquals(1, run.length)
        assertEquals(DayMark.Missed, run.week[0])
    }

    @Test
    fun `a gap with a freeze keeps the run and adds nothing`() {
        val answered = (13..15).map { day(it, answered = true) }
        val run = (answered + listOf(day(16), day(18))).playRun(LocalDate(2026, 11, 18))

        assertEquals(5, run.length)
        assertEquals(0, run.freezes)
        assertEquals(DayMark.Frozen, run.week[1])
    }

    @Test
    fun `three answers fill one freeze and a reader holds at most two`() {
        val nine = (1..9).map { day(it, answered = true) }

        val run = nine.playRun(LocalDate(2026, 11, 9))

        assertEquals(MAX_FREEZES, run.freezes)
        assertEquals(ANSWERS_PER_FREEZE - 1, run.freezeProgress)
    }

    @Test
    fun `an answer on a missed day counts before the day is judged`() {
        // The 23rd is missed, but its own answer is the third, so it fills the freeze that keeps it.
        val run = listOf(day(19), day(20, answered = true), day(21, answered = true), day(22), day(23, cleared = false, answered = true), day(24))
            .playRun(today)

        assertEquals(5, run.length)
        assertEquals(0, run.freezes)
        assertEquals(DayMark.Frozen, run.week[0])
    }

    @Test
    fun `a freeze is not spent on a run that has not started`() {
        val run = listOf(day(20, cleared = false, answered = true), day(21, cleared = false, answered = true), day(22, cleared = false, answered = true), day(24))
            .playRun(today)

        assertEquals(1, run.freezes)
        assertEquals(1, run.length)
    }

    @Test
    fun `days with play switched off read as missed and use the freezes the run has`() {
        val answered = (10..12).map { day(it, answered = true) }
        val run = (answered + day(20)).playRun(LocalDate(2026, 11, 20))

        assertEquals(1, run.length)
    }

    @Test
    fun `a zone change that moves today back a day keeps the run`() {
        val run = listOf(day(24), day(25)).playRun(LocalDate(2026, 11, 24))

        assertEquals(2, run.length)
    }

    @Test
    fun `days after today are still to come`() {
        val run = listOf(day(23)).playRun(today)

        assertEquals(DayMark.Cleared, run.week[0])
        assertEquals(listOf(DayMark.Ahead, DayMark.Ahead, DayMark.Ahead, DayMark.Ahead), run.week.drop(3))
    }

    /** NFR-3: five years of days folds well inside a frame. Printed, not asserted: CI hosts vary. */
    @Test
    fun `five years of days fold in well under a frame`() {
        val start = LocalDate(2021, 1, 1)
        val days = (0 until 1_800).map { PlayDay(start.plus(it, DateTimeUnit.DAY), clearedAtEpochMillis = 1L, answeredAtEpochMillis = if (it % 2 == 0) 1L else null) }
        val end = start.plus(1_799, DateTimeUnit.DAY)

        val took = TimeSource.Monotonic.measureTime { repeat(10) { days.playRun(end) } } / 10

        println("playRun over ${days.size} days: $took")
        assertEquals(1_800, days.playRun(end).length)
    }
}
