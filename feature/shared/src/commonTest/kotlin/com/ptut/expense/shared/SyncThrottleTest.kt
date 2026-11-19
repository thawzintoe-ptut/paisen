package com.ptut.expense.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SyncThrottleTest {

    private var now = Instant.fromEpochMilliseconds(1_000_000)
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    @Test
    fun `A resume within a minute of the last sync skips it`() {
        val throttle = SyncThrottle(clock)
        assertTrue(throttle.tryStart())

        now += 59.seconds

        assertFalse(throttle.tryStart())
    }

    @Test
    fun `A resume a minute later syncs again`() {
        val throttle = SyncThrottle(clock)
        throttle.tryStart()

        now += 60.seconds

        assertTrue(throttle.tryStart())
    }

    @Test
    fun `A clock set back does not hold syncs off`() {
        val throttle = SyncThrottle(clock)
        throttle.tryStart()

        now -= 3_600.seconds

        assertTrue(throttle.tryStart())
    }
}
