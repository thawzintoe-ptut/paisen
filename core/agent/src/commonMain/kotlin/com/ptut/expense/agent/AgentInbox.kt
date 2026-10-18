package com.ptut.expense.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * #205: one action waiting for the shell to open, the shape `sync/InviteLinks` gave invite links.
 * Siri, a Shortcut or the Android icon can start the app before anything has drawn, so the action
 * waits here until the graph is ready. A newer one replaces an older one nobody opened.
 */
object AgentInbox {
    private val pending = MutableStateFlow<ProposedAction?>(null)
    val pendingAction: StateFlow<ProposedAction?> = pending

    fun offer(action: ProposedAction) {
        pending.value = action
    }

    fun consume() {
        pending.value = null
    }
}
