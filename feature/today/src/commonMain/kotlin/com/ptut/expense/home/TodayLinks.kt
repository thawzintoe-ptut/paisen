package com.ptut.expense.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a Today toast's action asked to open. */
sealed interface TodayLink {
    /** #103: the caught toast's Change. */
    data class Edit(val transactionId: String) : TodayLink

    /** J05: the jar toast's See. */
    data class Jar(val goalId: String) : TodayLink
}

/**
 * A toast shows over every screen, so its action waits here for the shell rather than for Today,
 * the shape `agent/AgentInbox` has. A newer tap replaces one nobody opened.
 */
object TodayLinks {
    private val pending = MutableStateFlow<TodayLink?>(null)
    val pendingLink: StateFlow<TodayLink?> = pending.asStateFlow()

    fun offer(link: TodayLink) {
        pending.value = link
    }

    fun consume() {
        pending.value = null
    }
}
