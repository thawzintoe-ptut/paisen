package com.ptut.expense.importer

import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.ToastAction
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * #219: Discard and Not mine take a slip off the screen at once, offer Undo, and remove it only
 * once that Undo has left the screen (research finding 8). A slip shared into the app has no other
 * copy, so a mis-tap has to be reversible.
 *
 * The wait runs in [scope], not a screen's: Not mine leaves its screen at once. If the process
 * dies inside the window the discard never runs and the slip is still there, which is safe.
 */
class SlipDiscards(
    private val importer: SlipImporter,
    private val toasts: ToastMessages,
    private val scope: CoroutineScope,
    /** The longest a discard waits for its Undo to leave: no limit in the app, zero in a test. */
    private val window: () -> Duration,
) {

    private val waiting = MutableStateFlow<Map<String, Job>>(emptyMap())

    /** Slips taken off the screen whose discard has not run yet. */
    val pending: Flow<Set<String>> = waiting.map { it.keys }

    suspend fun discard(id: String) {
        if (id in waiting.value) return
        val left = CompletableDeferred<Unit>()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            withTimeoutOrNull(window()) { left.await() }
            // Once begun it finishes: a row gone with its picture left behind is an orphan.
            withContext(NonCancellable) { importer.discard(id) }
            waiting.update { it - id }
        }
        waiting.update { it + (id to job) }
        job.start()
        toasts.show(
            ToastMessage(
                text = AppText.copy.slips.slipRemoved,
                tone = ToastTone.Deleted,
                // Run by the shell after this screen has gone, so it holds only the id.
                action = ToastAction(AppText.copy.common.undo) { undo(id) },
                onGone = { left.complete(Unit) },
            ),
        )
    }

    fun undo(id: String) {
        val job = waiting.value[id] ?: return
        job.cancel()
        waiting.update { it - id }
    }
}
