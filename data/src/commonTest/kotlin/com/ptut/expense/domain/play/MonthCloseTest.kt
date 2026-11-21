package com.ptut.expense.domain.play

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReviewReason
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

    @Test
    fun `slips dated in the next month are not counted as waiting`() {
        val slips = listOf(slip("dec", LocalDate(2026, 12, 2)), slip("nov", LocalDate(2026, 11, 28)))

        assertEquals(1, slips.waitingIn("2026-11", BangkokTime))
        assertEquals(0, listOf(slip("dec", LocalDate(2026, 12, 2))).waitingIn("2026-11", BangkokTime))
    }

    @Test
    fun `a row both risen and large shows once in the big costs`() {
        val rows = listOf(row("old", LocalDate(2026, 10, 5), 10_000, shop = "Shopee")) +
            (1..5).map { row("s$it", LocalDate(2026, 11, it), 10_000) } +
            row("big", LocalDate(2026, 11, 9), 129_000, shop = "Shopee")

        val close = rows.monthClose("2026-11", BangkokTime)

        assertEquals(listOf(BigCost("big", risen = true, large = true)), close.bigCosts)
    }

    private fun slip(id: String, day: LocalDate) = row(id, day, 0).let {
        QueuedSlip(id, "Shop", it.occurredAtEpochMillis, "", null, ReviewReason.AmountUnclear, it.occurredAtEpochMillis)
    }
}
