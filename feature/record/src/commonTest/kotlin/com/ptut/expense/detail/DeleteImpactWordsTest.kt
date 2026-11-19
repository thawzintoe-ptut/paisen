package com.ptut.expense.detail

import com.ptut.expense.domain.LedgerPeriod
import com.ptut.expense.domain.periodsMovedByDeleting
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** A Wednesday, so the week it sits in has days on both sides of it. */
private val today = LocalDate(2026, 8, 19)

class DeleteImpactWordsTest {

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
