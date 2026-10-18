package com.ptut.expense.sync.push

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A push that arrived while the app was open: no banner, a sync at once. The platform offers the
 * group id; the shell collects it while it is on screen and runs `syncAll`. Extra buffer so a
 * burst is not dropped.
 */
object PushEvents {
    private val arrived = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val groupIds: SharedFlow<String> = arrived

    /** False when no screen is open to sync it, so Android shows a notification instead. */
    fun offer(groupId: String): Boolean = arrived.subscriptionCount.value > 0 && arrived.tryEmit(groupId)
}

/**
 * A tapped notification's group, waiting for the shell as an invite link does in `InviteLinks`:
 * the tap can arrive before the graph exists.
 */
object GroupLinks {
    private val pending = MutableStateFlow<String?>(null)
    val pendingGroupId: StateFlow<String?> = pending

    fun offer(groupId: String) {
        if (groupId.isNotBlank()) pending.value = groupId
    }

    fun consume() {
        pending.value = null
    }
}
