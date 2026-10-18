package com.ptut.expense.domain.ledger

import com.ptut.expense.domain.Transaction

/**
 * What the ledger has to say right now: either it has not answered, or these are the rows.
 *
 * The seam used to hand back a bare `List<Transaction>`, which meant `emptyList()` stood for two
 * different facts — "Room has not run the query yet" and "the ledger is genuinely empty". Every
 * consumer had to remember which, and two of them separately grew a workaround for guessing
 * wrong: the repository started its queries `Eagerly` so a late subscriber would not meet the
 * flash, and the detail screen read an empty ledger as still-loading so it would not say a record
 * was gone before it had looked.
 *
 * This is the same move `com.ptut.expense.ui.DisplayAmount` makes for an amount the reader could
 * not make out, and for the same stated reason: in the type, the next person cannot forget.
 *
 * Deliberately without a `rowsOrEmpty()` convenience. That helper is the nullable version wearing
 * a different hat — it lets one call site collapse the two cases quietly, which is the failure
 * this type exists to prevent. A screen that does not care still has to say so out loud.
 */
sealed interface LedgerSnapshot {

    /** The query has not produced a first result. Never the same as a ledger holding nothing. */
    data object Unanswered : LedgerSnapshot

    /** What the ledger holds, which may legitimately be nothing at all. */
    data class Rows(val transactions: List<Transaction>) : LedgerSnapshot
}

/**
 * The row with this id, or null because there is none to act on.
 *
 * For the two sites that resolve an id in order to *do* something — deleting a record, saving a
 * correction. Both decline on null, and both must decline on [LedgerSnapshot.Unanswered] too: a
 * ledger that has not answered cannot supply the row a write needs.
 *
 * This is not the `rowsOrEmpty()` the type deliberately omits, and the difference is the one that
 * matters. A list going to the screen must keep the two cases apart, because "nothing here" and
 * "not yet" render differently and FR-20 turns on it. A single row going to a write does not: no
 * row is no write either way. Collapsing them is safe precisely where nothing gets rendered.
 */
fun LedgerSnapshot.rowWithId(transactionId: String): Transaction? = when (this) {
    LedgerSnapshot.Unanswered -> null
    is LedgerSnapshot.Rows -> transactions.firstOrNull { it.id == transactionId }
}
