package com.ptut.expense.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** What a haptic confirms (#114): a write that landed, or a delete. */
enum class HapticEvent { Saved, Deleted }

/**
 * Every save and delete gets a haptic, so the reader knows it landed without looking (#114).
 *
 * Raised by the state holder that did the write and played by the shell, which owns the view:
 * the same split as [ToastMessages], for the same reason. The shell plays [HapticEvent.Saved] as
 * Compose's Confirm and [HapticEvent.Deleted] as Reject, which follow the system haptics setting.
 */
class Haptics {

    /** Buffered, so a write never waits on the shell; a haptic nobody plays is simply dropped. */
    private val channel = Channel<HapticEvent>(Channel.BUFFERED)

    val events: Flow<HapticEvent> = channel.receiveAsFlow()

    fun saved() {
        channel.trySend(HapticEvent.Saved)
    }

    fun deleted() {
        channel.trySend(HapticEvent.Deleted)
    }
}
