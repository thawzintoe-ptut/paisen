package com.ptut.expense.settings

import com.ptut.expense.domain.BangkokTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class TimezoneViewModelTest {

    @Test
    fun `the window runs midnight to midnight on the Bangkok day`() {
        val state = TimezoneViewModel(at(2026, 8, 24, hour = 19)).uiState

        assertEquals("Asia/Bangkok", state.zone)
        assertEquals("Mon 24 Aug", state.today)
        assertEquals("24 Aug 00:00 → 25 Aug 00:00", state.window)
    }

    /** FR-19's whole point: 23:30 UTC is already tomorrow in Bangkok, and the app says so. */
    @Test
    fun `late evening in London is already the next Bangkok day`() {
        val state = TimezoneViewModel(at(2026, 8, 24, hour = 23, zone = TimeZone.UTC)).uiState

        assertEquals("Tue 25 Aug", state.today)
        assertEquals("25 Aug 00:00 → 26 Aug 00:00", state.window)
    }

    @Test
    fun `the last day of a month rolls into the next one`() {
        val state = TimezoneViewModel(at(2026, 8, 31, hour = 22)).uiState

        assertEquals("31 Aug 00:00 → 1 Sep 00:00", state.window)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, zone: TimeZone = BangkokTime): Clock {
        val instant = LocalDateTime(year, month, day, hour, 0).toInstant(zone)
        return object : Clock {
            override fun now(): Instant = instant
        }
    }
}
