package com.ptut.expense.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DayLabelTest {

    @Test
    fun readsAsAShortWeekdayDayAndMonth() {
        assertEquals("Fri 14 Aug", LocalDate(2026, 8, 14).dayLabel())
    }
}
