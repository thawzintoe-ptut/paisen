package com.ptut.expense.sync

/**
 * The one place sync writes a line to the system log (logcat, the Xcode console). Sync fails
 * quietly by design, so a line here is the only trace: say what kind of thing and the status,
 * never a name, an amount, a token or a payload.
 */
internal fun syncLog(message: String) {
    println("PaiSen sync: $message")
}
