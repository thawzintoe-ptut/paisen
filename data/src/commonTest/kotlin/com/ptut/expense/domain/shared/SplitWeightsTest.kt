package com.ptut.expense.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitWeightsTest {

    @Test
    fun aPercentTakesTwoDecimalsAndAtMost999Point99() {
        assertEquals(99_999L, parsePercent("999.99"))
        assertEquals(3_333L, parsePercent("33.33"))
        assertEquals(5_000L, parsePercent("50"))
        assertNull(parsePercent("1000"))
        assertNull(parsePercent("12.345"))
        assertNull(parsePercent("1.2.3"))
        assertNull(parsePercent(""))
    }

    @Test
    fun basisPointsReadBackAsTheyWereTyped() {
        assertEquals("33.33", percentText(3_333))
        assertEquals("50", percentText(5_000))
        assertEquals("12.5", percentText(1_250))
        assertEquals("0.05", percentText(5))
    }

    @Test
    fun aShareCountsWholeAndSaysWhenItDropsADecimal() {
        assertEquals(1L, wholeShares("1.5"))
        assertEquals(2L, wholeShares("2"))
        assertEquals(0L, wholeShares(""))
        assertEquals(0L, wholeShares(".5"))
        assertTrue(dropsShareDecimal("1.5"))
        assertFalse(dropsShareDecimal("1.0"))
        assertFalse(dropsShareDecimal("2"))
    }
}
