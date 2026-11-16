package com.ptut.expense.platform.lock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Whether the ledger is hidden behind the lock screen (#25).
 *
 * It opens locked when the switch is on, and locks again only after [grace] in the background.
 * The photo picker, the share sheet, a file export and the unlock prompt itself all send the app
 * to the background for a moment, and relocking on each would ask for a finger every few seconds.
 */
class AppLock(
    private val enabled: () -> Boolean,
    private val nowMillis: () -> Long,
    private val grace: Duration = DEFAULT_GRACE,
) {

    private val isLocked = MutableStateFlow(enabled())
    private var leftAtMillis: Long? = null

    val locked: StateFlow<Boolean> = isLocked.asStateFlow()

    fun movedToBackground() {
        leftAtMillis = nowMillis()
    }

    fun movedToForeground() {
        val leftAt = leftAtMillis ?: return
        leftAtMillis = null
        if (enabled() && nowMillis() - leftAt >= grace.inWholeMilliseconds) isLocked.value = true
    }

    /** The person passed the phone's check, or switched the lock off. */
    fun unlock() {
        isLocked.value = false
    }

    companion object {
        val DEFAULT_GRACE: Duration = 30.seconds
    }
}
