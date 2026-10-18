package com.ptut.expense.ui

/**
 * One labelled fact about a transaction: a glyph, what it is, and what it says.
 *
 * A list of these rather than named fields — they are drawn identically, so naming them would
 * only move a `when` into the view for no gain.
 *
 * It lives in `ui/` rather than in `detail/` because the edit screen draws the same rows for the
 * fields it refuses to change. A second copy is how two screens showing one transaction start
 * disagreeing about what a row looks like.
 */
data class TransactionMetaRow(
    val label: String,
    val value: String,
    val icon: String,
)

/**
 * The glyphs a meta row draws, named once.
 *
 * The record screen and the edit screen label the same facts, and one name written in two files is
 * how they start drawing different pictures for one row. An unknown name throws when it is drawn
 * rather than when it is compiled, which is the other reason to write each one in a single place.
 */
object MetaGlyph {

    const val Merchant = "store"

    const val Time = "schedule"

    /** Category and reference share it: neither pictures what was bought, both are a filing code. */
    const val Tag = "sell"

    const val Source = "auto_awesome"

    /** The reader's own words about the row (#210). */
    const val Note = "edit"
}
