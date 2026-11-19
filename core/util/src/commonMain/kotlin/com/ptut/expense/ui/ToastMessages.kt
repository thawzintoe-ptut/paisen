package com.ptut.expense.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Whether the line is reporting success or refusing to act.
 *
 * The tone is carried rather than inferred from the words, because the icon is the part read
 * first: a refusal under a tick reads as "saved" for the half-second it takes to read the
 * sentence, which is long enough to walk away from a slip that was never filed (plan §G item 6).
 */
enum class ToastTone {
    Success,
    Error,

    /** Something was removed. A bin shuts on it, so a delete never reads as a save under a tick. */
    Deleted,
}

/** One line of confirmation. Raised by whatever did the work, worded by whatever knows the copy. */

/**
 * The one thing a toast can offer besides words. FR-21's undo is the only caller, and it is a
 * toast rather than a control on the screen because the screen it belongs to is already gone —
 * a delete pops back to the list before the reader has decided whether they meant it.
 *
 * [onAction] suspends and is run by the shell, not the caller: the state holder that raised the
 * message has usually died by the time this is tapped, so its scope cannot be the one that runs.
 */
data class ToastAction(val label: String, val onAction: suspend () -> Unit)

data class ToastMessage(
    val text: String,
    val tone: ToastTone = ToastTone.Success,
    val action: ToastAction? = null,
    /**
     * Research finding 8: run when the line leaves without its action, replaced by the next one or
     * left behind on another screen. What waited on the Undo can happen now.
     */
    val onGone: (() -> Unit)? = null,
)

/**
 * The app-level toast seam. A save is "close the sheet → return to Today → *then* say so", and a
 * host owned by the sheet would die at step one, so the channel lives above every screen.
 *
 * Plan §E puts this channel on `AppViewModel`. It sits one level below instead, because the state
 * holder that raises a toast is not the one that renders it, and a ViewModel that reaches for
 * another ViewModel is a worse dependency than a seam both can be given.
 */
class ToastMessages {

    /**
     * Buffered, so raising a toast never waits for the shell to be listening — a save must not
     * hang on a screen that is mid-transition.
     */
    private val channel = Channel<ToastMessage>(Channel.BUFFERED)

    val messages: Flow<ToastMessage> = channel.receiveAsFlow()

    suspend fun show(message: ToastMessage) = channel.send(message)
}
