package com.ptut.expense.domain.play

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Satang
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MonthCloseTest {

    @Test
    fun `the same shop amount and day is flagged as a possible duplicate`() {
        val rows = listOf(row("a", LocalDate(2026, 11, 3), 12_000, shop = "Lawson"), row("b", LocalDate(2026, 11, 3), 12_000, shop = "lawson "))

        assertEquals(listOf("a" to "b"), rows.monthClose("2026-11", BangkokTime).duplicates)
    }

    @Test
    fun `a shop paid last month that came to more is a risen repeat`() {
        val rows = listOf(row("old", LocalDate(2026, 10, 5), 29_900, shop = "Netflix"), row("new", LocalDate(2026, 11, 5), 34_900, shop = "Netflix"))

        val close = rows.monthClose("2026-11", BangkokTime)

        assertEquals(listOf("new"), close.risenRepeats)
        assertEquals(Satang(5_000), close.versusLastMonth)
    }

    @Test
    fun `large costs are well above the month's usual row biggest first`() {
        val rows = (1..5).map { row("s$it", LocalDate(2026, 11, it), 10_000) } + row("big", LocalDate(2026, 11, 9), 90_000)

        val close = rows.monthClose("2026-11", BangkokTime)

        assertEquals(listOf("big"), close.large)
        assertNull(close.versusLastMonth)
    }
}
