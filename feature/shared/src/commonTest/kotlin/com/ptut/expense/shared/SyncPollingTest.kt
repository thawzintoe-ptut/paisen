package com.ptut.expense.shared

import com.ptut.expense.sync.SyncOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SyncPollingTest {

    @Test
    fun `Each tick runs the pull the screen hands in`() = runTest {
        var pulls = 0
        val polling = syncPolling(MutableStateFlow(true), runner = null, every = 15.seconds) {
            pulls++
            PULLED
        }

        backgroundScope.launch { polling.collect {} }
        runCurrent()
        advanceTimeBy(31.seconds)

        assertEquals(3, pulls)
    }

    @Test
    fun `Each failed pull doubles the wait before the next`() = runTest {
        var pulls = 0
        val polling = syncPolling(MutableStateFlow(true), runner = null, every = 15.seconds) {
            pulls++
            error("offline")
        }

        backgroundScope.launch { polling.collect {} }
        runCurrent()
        advanceTimeBy(29.seconds)
        assertEquals(1, pulls)
        advanceTimeBy(2.seconds)
        assertEquals(2, pulls)
        advanceTimeBy(58.seconds)
        assertEquals(2, pulls)
        advanceTimeBy(2.seconds)

        assertEquals(3, pulls)
    }

    @Test
    fun `A pull that keeps failing waits no longer than the cap`() = runTest {
        val pulledAt = mutableListOf<Long>()
        val polling = syncPolling(MutableStateFlow(true), runner = null, every = 15.seconds) {
            pulledAt += testScheduler.currentTime
            error("offline")
        }

        backgroundScope.launch { polling.collect {} }
        runCurrent()
        advanceTimeBy(20.minutes)

        val waits = pulledAt.zipWithNext { earlier, later -> (later - earlier).milliseconds }
        assertEquals(listOf(30.seconds, 60.seconds, 120.seconds, 240.seconds, MAX_POLL_WAIT, MAX_POLL_WAIT), waits.take(6))
        assertTrue(waits.all { it <= MAX_POLL_WAIT })
    }

    @Test
    fun `A pull that works again brings the wait back to the tick`() = runTest {
        var pulls = 0
        val polling = syncPolling(MutableStateFlow(true), runner = null, every = 15.seconds) {
            pulls++
            if (pulls <= 2) error("offline")
            PULLED
        }

        backgroundScope.launch { polling.collect {} }
        runCurrent()
        // Fails at 0 and 30, works at 90, then ticks every 15 again.
        advanceTimeBy(91.seconds)
        assertEquals(3, pulls)
        advanceTimeBy(15.seconds)

        assertEquals(4, pulls)
    }

    @Test
    fun `A group pull that failed backs off as a thrown one does`() = runTest {
        var pulls = 0
        val polling = syncPolling(MutableStateFlow(true), runner = null, every = 15.seconds) {
            pulls++
            SyncOutcome(groupsChanged = false, failed = true)
        }

        backgroundScope.launch { polling.collect {} }
        runCurrent()
        advanceTimeBy(29.seconds)
        assertEquals(1, pulls)
        advanceTimeBy(2.seconds)
        assertEquals(2, pulls)
        advanceTimeBy(58.seconds)

        assertEquals(2, pulls)
    }

    @Test
    fun `One sync says whether the pull worked`() = runTest {
        assertTrue(syncOnce(runner = null) { PULLED })
        assertFalse(syncOnce(runner = null) { error("offline") })
        assertFalse(syncOnce(runner = null) { SyncOutcome(groupsChanged = true, failed = true) })
    }

    @Test
    fun `A cancelled pull is not taken for a failure`() = runTest {
        assertFailsWith<CancellationException> {
            syncOnce(runner = null) { throw CancellationException("screen left") }
        }
    }

    private companion object {
        val PULLED = SyncOutcome(groupsChanged = false, failed = false)
    }
}
