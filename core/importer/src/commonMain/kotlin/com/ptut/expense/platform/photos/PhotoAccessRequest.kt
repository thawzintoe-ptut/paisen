package com.ptut.expense.platform.photos

/**
 * The system photo dialog, raised on purpose by the one screen that offers it. FR-2.
 *
 * A plain interface bound per platform, like [com.ptut.expense.platform.files.LedgerFileStore] —
 * both are a system sheet, and the sheet is the platform-specific part. It also means the first-run
 * state holder can be driven by a fake in the host suite, where no dialog can exist.
 *
 * Nothing is returned, and nothing is thrown. A refusal is not this seam's news to break: the scan
 * that follows fails on its own and journals why, which is what puts the reason on the Today banner
 * and in Settings instead of in a dialog that has already gone. First run sits outside the shell
 * that draws toasts, so an implementation that threw would have nowhere for the message to land —
 * every one of them answers instead.
 */
interface PhotoAccessRequest {

    /** Shows the dialog if it has never been answered, and returns once it has closed. */
    suspend fun ask()
}
