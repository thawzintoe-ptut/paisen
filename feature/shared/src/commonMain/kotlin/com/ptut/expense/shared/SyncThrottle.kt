package com.ptut.expense.shared

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Lets a sync start at most once per [gap]. Each trip to the photo picker, a bank app or the share
 * sheet resumes the app, and an unthrottled sync there costs reads every time.
 */
class SyncThrottle(private val clock: Clock = Clock.System, private val gap: Duration = 60.seconds) {
    private var last: Instant? = null

    /** True, and the wait starts again, when the last sync began at least [gap] ago or the clock went back. */
    fun tryStart(): Boolean {
        val now = clock.now()
        val previous = last
        if (previous != null && now - previous in Duration.ZERO..<gap) return false
        last = now
        return true
    }
}
