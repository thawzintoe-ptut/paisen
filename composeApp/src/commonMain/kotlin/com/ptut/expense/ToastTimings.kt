package com.ptut.expense

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The shell's toast clock; the defaults match PtutMotion.Default so the VM needs no design import. */
data class ToastTimings(
    val push: Duration = 300.milliseconds,
    val toastHold: Duration = 2_600.milliseconds,
    val undoWindow: Duration = 4.seconds,
    val spokenUndoWindow: Duration = 20.seconds,
) {
    fun undoWindowFor(screenReader: Boolean): Duration = if (screenReader) spokenUndoWindow else undoWindow
}
