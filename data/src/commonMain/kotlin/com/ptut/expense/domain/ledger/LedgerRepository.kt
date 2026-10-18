package com.ptut.expense.domain.ledger

import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import kotlinx.coroutines.flow.StateFlow

/**
 * The ledger seam. Room sits behind this; a screen never sees a DAO.
 *
 * The three transaction flows carry a [LedgerSnapshot] rather than a bare list, so "the query has
 * not answered" and "the ledger is empty" cannot arrive as the same value.
 *
 * It lives in `domain/` because two feature packages already read it — a repository interface
 * owned by one of them is a dependency pointing the wrong way.
 */
interface LedgerRepository {
    val todaysTransactions: StateFlow<LedgerSnapshot>

    /** Enough history for the reports to compare a period against the one before it. */
    val recentTransactions: StateFlow<LedgerSnapshot>

    /**
     * The whole ledger, newest first. The Ledger screen audits against this, so it is not
     * [recentTransactions] with a bigger window — a list that quietly ends at a horizon is how
     * a month stops adding up and nobody can see why.
     */
    val allTransactions: StateFlow<LedgerSnapshot>

    /**
     * Time since the last import that finished successfully, or null when none ever has.
     *
     * Null rather than a sentinel: a zero would read as a healthy ledger, and a very large
     * number would reach the banner as a span nobody can read. Absence is the honest answer,
     * and [com.ptut.expense.domain.ImportFreshness.NeverRun] is the name it carries on screen.
     */
    val sinceLastImportMillis: StateFlow<Long?>

    val queuedSlips: StateFlow<List<QueuedSlip>>

    /**
     * What the importer did and what it could not do, newest first. FR-5, US-8.
     *
     * The Activity screen is where a silently broken import becomes visible before it distorts a
     * month, so it reads the journal itself rather than the derived freshness above.
     */
    val importRuns: StateFlow<List<ImportRun>>

    /**
     * Writes one row and returns once it is durable. Nothing is handed back: every screen already
     * observes the tables this touches, so the row arrives the same way an imported one does.
     */
    suspend fun record(draft: TransactionDraft)

    /**
     * FR-21. Removes the row outright rather than marking it — see [restore] for why.
     *
     * Deleting an id the ledger no longer holds is not an error. The undo window means two
     * screens can ask about the same row, and a throw there would crash on a race the reader
     * cannot see.
     */
    suspend fun delete(transactionId: String)

    /**
     * Puts a deleted row back exactly as it was, id included.
     *
     * The pair is a hard delete plus this, not a soft delete swept later. A soft delete would
     * put a `deletedAt` filter on every query in the app for the sake of a six-second window,
     * and both designs behave the same when the process dies mid-window: the delete stands.
     * Holding the row in memory keeps that cost in the one place that needs it.
     */
    suspend fun restore(transaction: Transaction)

    /**
     * FR-21's correction. Writes the fields screen 11c owns and stamps the row edited. A
     * sub-category not under the edit's category is not stored (#110).
     * Everything else — the merchant, the reference, the instant, the source and the slip's own
     * reading — is left exactly as it was, because those are what the correction is measured
     * against.
     *
     * The stamp is taken here rather than passed in, for the same reason [record] mints its own
     * id: when a row was corrected is storage's business, not a screen's.
     *
     * Editing an id the ledger no longer holds writes nothing, and is not an error. A row can be
     * deleted from another screen while this one is open, and a throw there would crash on a race
     * the reader cannot see.
     */
    suspend fun applyEdit(transactionId: String, edit: TransactionEdit)
}
