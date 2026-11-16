package com.ptut.expense.platform.photos

/**
 * The system page where the reader gives PaiSeN every photo, for Today's Fix when scans fail.
 *
 * Neither platform lets an app open its Photos permission row directly, so each one opens the
 * deepest page it can and words its own [steps] for the taps left after that.
 */
interface PhotoSettings {

    /** What to tap once the page is open, in the app's language as it is now. */
    val steps: String

    /** True while the reader shared only the photos they picked, so new slips stay hidden. Android only. */
    val onlyPickedPhotos: Boolean get() = false

    fun open()
}
