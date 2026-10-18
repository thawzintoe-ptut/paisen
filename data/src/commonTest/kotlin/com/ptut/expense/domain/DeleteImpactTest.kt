package com.ptut.expense.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** A Wednesday, so the week it sits in has days on both sides of it. */
private val today = LocalDate(2026, 8, 19)

class DeleteImpactTest {

    @Test
    fun aRowFromLastWeekMovesTheMonthButNotTheWeek() {
        assertEquals(
            listOf(LedgerPeriod.ThisMonth),
            periodsMovedByDeleting(occurredAt = LocalDate(2026, 8, 12), today = today),
        )
    }

    @Test
    fun aRowFromLastMonthMovesNoneOfTheThree() {
        assertEquals(
            emptyList(),
            periodsMovedByDeleting(occurredAt = LocalDate(2026, 7, 30), today = today),
        )
    }

    /** Monday is the week's first day, so the boundary is the one worth pinning. */
    @Test
    fun aRowFromMondayIsStillInsideThisWeek() {
        assertEquals(
            listOf(LedgerPeriod.ThisWeek, LedgerPeriod.ThisMonth),
            periodsMovedByDeleting(occurredAt = LocalDate(2026, 8, 17), today = today),
        )
    }

    @Test
    fun aRowFromTodayMovesAllThreeTotals() {
        assertEquals(
            listOf(LedgerPeriod.Today, LedgerPeriod.ThisWeek, LedgerPeriod.ThisMonth),
            periodsMovedByDeleting(occurredAt = today, today = today),
        )
    }

    @Test
    fun threePeriodsReadAsASentenceRatherThanAList() {
        assertEquals(
            "today, this week and this month",
            periodsMovedByDeleting(occurredAt = today, today = today).phrase(),
        )
    }

    @Test
    fun onePeriodIsNamedOnItsOwn() {
        assertEquals(
            "this month",
            periodsMovedByDeleting(occurredAt = LocalDate(2026, 8, 12), today = today).phrase(),
        )
    }

    /** The caller writes its own sentence for this, so the phrase must not trail an "and". */
    @Test
    fun nothingMovedIsAnEmptyPhrase() {
        assertEquals("", emptyList<LedgerPeriod>().phrase())
    }
}
