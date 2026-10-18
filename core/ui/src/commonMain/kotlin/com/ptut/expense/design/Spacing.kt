package com.ptut.expense.design

import androidx.compose.ui.unit.dp

/**
 * The handoff's gaps. The t-shirt names are the 8dp ramp the layout leans on; the numbered
 * ones are the off-ramp steps the design also uses, named for their value because there is
 * no room left in a six-step scale for eight more. Raw Dp literals do not belong in reusable UI.
 */
object Spacing {
    /** For the arm of a conditional gap that adds nothing. */
    val none = 0.dp

    val xxs = 4.dp
    val xs = 8.dp
    val sm = 16.dp
    val md = 24.dp
    val lg = 32.dp
    val xl = 48.dp

    val s2 = 2.dp
    val s6 = 6.dp
    val s10 = 10.dp
    val s11 = 11.dp
    val s12 = 12.dp
    val s13 = 13.dp
    val s14 = 14.dp
    val s15 = 15.dp
    val s18 = 18.dp
    val s20 = 20.dp
    val s22 = 22.dp
    val s26 = 26.dp
    val s28 = 28.dp
    val s34 = 34.dp
}

/** Fixed sizes the layout leans on. Same reason as [Spacing]: no literals in the UI. */
object Sizes {
    /** A Settings row's leading glyph and its chevron. */
    val settingsGlyph = 22.dp
    val settingsTrailing = 20.dp

    /** The design's floor for anything tappable. The brand bar draws [touchTarget] instead. */
    val minTouchTarget = 44.dp
    val touchTarget = 48.dp

    val medallionGlyph = 36.dp

    /** The status line's own glyph — smaller than a row icon; it is punctuation, not content. */
    val statusGlyph = 18.dp

    /** The check mark inside the toast. Smaller than a row icon; it is punctuation, not content. */
    val toastIcon = 21.dp

    /**
     * The undo window draining under the toast's words. A hairline: it is a countdown the reader
     * glances at, and a bar heavy enough to read as a control would invite a tap it cannot take.
     */
    val undoBar = 3.dp

    /** The kept-slip placeholder: 64 wide by 80 tall, so it reads as a slip and not a tile. */
    val slipThumbWidth = 64.dp
    val slipThumb = 80.dp

    /** The slip image on confirm slip, big enough to read an amount off. */
    val slipPanelWidth = 118.dp
    val slipPanelHeight = 170.dp

    /** The glyph in Edit category's circle, larger than a row's because the category is the subject. */
    val identityTileGlyph = 27.dp

    /** How tall a sheet's list of choices may get before it scrolls instead of growing. */
    val pickerHeight = 320.dp
}
