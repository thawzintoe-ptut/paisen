package com.ptut.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.design.PtutMotion
import com.ptut.expense.domain.folders.FirstRunFolders
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The shell's state holder. It owns the one thing no screen can own: what the app is saying on top
 * of whatever screen is showing, and for how long.
 *
 * The clock lives here rather than in a `LaunchedEffect` so that a recomposition — a tab switch, a
 * rotation — cannot restart a toast that was half-way through its two and a half seconds.
 */
class AppViewModel(
    toasts: ToastMessages,
    importer: SlipImporter,
    /**
     * Null in a test that is only about toasts. On a device it is always bound — US-1's promise
     * is that a fresh install imports without being set up, and this is what keeps it.
     */
    firstRun: FirstRunFolders? = null,
    private val motion: PtutMotion = PtutMotion.Default,
) : ViewModel() {

    private val shown = MutableStateFlow<ToastMessage?>(null)

    /** Null means nothing is being said. */
    val toast: StateFlow<ToastMessage?> = shown.asStateFlow()

    private var shownAt: TimeMark = TimeSource.Monotonic.markNow()

    init {
        // NFR-4 on app start, not only after a run. An app that has not imported for a month must
        // still let go of the evidence, and the importer is the only thing that can.
        viewModelScope.launch { importer.sweep() }

        // Before the first scan rather than after it: a run over no folders is a run that found
        // nothing, and it would journal that as a healthy empty import.
        viewModelScope.launch { firstRun?.seed() }

        viewModelScope.launch {
            // collectLatest: a second save inside the window replaces the line and restarts the
            // clock. Queueing would leave a toast about a row the reader saved five seconds ago.
            toasts.messages.collectLatest { message ->
                shown.value?.takeIf { it !== message }?.let(::leave)
                shown.value = message
                shownAt = TimeSource.Monotonic.markNow()
                // An Undo stays longer than a plain line, and leaves sooner if the next line or
                // screen comes first. Undo already tapped means the line is gone, so nothing runs.
                delay(if (message.action != null) motion.undoWindow else motion.toastHold)
                if (shown.value === message) {
                    shown.value = null
                    leave(message)
                }
            }
        }
    }

    /**
     * Runs the showing message's action and takes the line away, because the thing it offered has
     * now happened. Nothing to run is not an error: the toast can leave between the tap landing
     * and this being reached.
     */
    fun runToastAction() {
        val action = shown.value?.action ?: return
        shown.value = null
        viewModelScope.launch { action.onAction() }
    }

    /**
     * The shell moved to another screen. An Undo raised in the moment before it stays: a delete
     * offers its Undo as its own screen closes, and that move is the delete, not the reader moving on.
     */
    fun screenChanged() {
        val showing = shown.value ?: return
        if (showing.action == null || shownAt.elapsedNow() < motion.push) return
        shown.value = null
        leave(showing)
    }

    private fun leave(message: ToastMessage) {
        message.onGone?.invoke()
    }
}
