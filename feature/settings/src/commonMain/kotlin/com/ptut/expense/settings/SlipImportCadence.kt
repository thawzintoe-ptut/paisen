package com.ptut.expense.settings

/**
 * What Settings may honestly say about importing on this platform.
 *
 * The rows are one value because they are one claim read several times: where slips come from, how
 * often anything looks, and what the reader can do about it. Android schedules a job every fifteen
 * minutes; iOS has no equivalent and catches up when the app is opened. C-4 — a row that offered a
 * switch iOS cannot honour would be worse than no row at all.
 *
 * Copy rather than a flag the view branches on, so the wording sits beside the platform that has
 * to be true to it instead of inside a `when` in a shared screen.
 */
data class SlipImportCadence(
    /** The value under "Photo folders": which folder, and what watches it. */
    val slipSource: String,
    /** What stands in front of the last-run phrase under "Background import". */
    val importRhythm: String,
    /** False where the row states a fact. The view drops the chevron and stops answering taps. */
    val importIsAdjustable: Boolean,
    /**
     * The paragraph the background import screen opens with. It is the one place the platform's
     * limit is explained in full, which is why it lives beside the two lines that summarise it.
     */
    val importExplanation: String,
)

/** Android schedules a job; iOS scans on foreground return. Neither can speak for the other. */
expect fun slipImportCadence(): SlipImportCadence
