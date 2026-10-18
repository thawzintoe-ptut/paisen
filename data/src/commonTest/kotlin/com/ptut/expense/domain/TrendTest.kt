package com.ptut.expense.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrendTest {

    @Test
    fun reportsPercentChangeAgainstThePreviousPeriod() {
        val trend = trendBetween(previous = Satang(100_000), current = Satang(120_000))

        assertEquals(20, trend.percent)
        assertEquals(TrendDirection.Up, trend.direction)
    }

    @Test
    fun hasNoPercentWhenThereIsNoBaseline() {
        val trend = trendBetween(previous = Satang(0), current = Satang(50_000))

        assertNull(trend.percent)
        assertEquals(TrendDirection.Up, trend.direction)
    }

    @Test
    fun isFlatWhenNothingMoved() {
        val trend = trendBetween(previous = Satang(50_000), current = Satang(50_000))

        assertEquals(0, trend.percent)
        assertEquals(TrendDirection.Flat, trend.direction)
    }
}
