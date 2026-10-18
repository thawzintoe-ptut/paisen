package com.ptut.expense.platform.work

/**
 * The platform's own periodic scan, started and stopped.
 *
 * Bound only where one exists. C-4 leaves iOS with no window it can rely on, so nothing binds this
 * there and the screen states a fact instead of drawing a switch that would turn off a job that
 * was never scheduled.
 */
interface BackgroundImport {

    /** Schedules the recurring scan, and runs one now so the reader sees the switch take effect. */
    fun start()

    /** Cancels the recurring scan. A scan the reader asks for by opening the app still runs. */
    fun stop()
}
