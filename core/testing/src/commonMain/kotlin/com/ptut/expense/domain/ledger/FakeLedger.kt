package com.ptut.expense.domain.ledger

import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A ledger holding exactly the rows a test hands it, newest first.
 *
 * Not [SampleLedgerRepository], which is a populated fixture for screens that need a plausible
 * ledger: this one holds nothing unless a test says otherwise, so an assertion reads against the
 * rows in front of it rather than against nineteen it did not write.
 *
 * It replaces the near-identical private copies that had grown in the screen tests.
 */
class FakeLedger(
    rows: List<Transaction> = emptyList(),
    /** Null is a ledger nothing has ever imported into, which is what a fresh install meets. */
    sinceLastImport: Long? = null,
    queued: List<QueuedSlip> = emptyList(),
) : LedgerRepository {

    private val ledger =
        MutableStateFlow<LedgerSnapshot>(LedgerSnapshot.Rows(rows.newestFirst()))

    override val todaysTransactions: StateFlow<LedgerSnapshot> =
        MutableStateFlow(LedgerSnapshot.Rows(emptyList()))

    override val recentTransactions: StateFlow<LedgerSnapshot> = ledger

    override val allTransactions: StateFlow<LedgerSnapshot> = ledger

    override val sinceLastImportMillis: StateFlow<Long?> = MutableStateFlow(sinceLastImport)

    override val importRuns: StateFlow<List<ImportRun>> = MutableStateFlow(emptyList())

    override val queuedSlips: StateFlow<List<QueuedSlip>> = MutableStateFlow(queued)

    /**
     * Replaces the whole ledger, the way a delete or a correction reaches a screen that is already
     * collecting. A screen that reads its row once instead of observing fails here, not on a phone.
     */
    fun rewrite(rows: List<Transaction>) {
        ledger.value = LedgerSnapshot.Rows(rows.newestFirst())
    }

    /**
     * What the ledger holds right now. Throws while it is unanswered, because every writer below
     * is a test acting on rows it just handed in — reaching one before answering is the test's bug.
     */
    private val currentRows: List<Transaction> get() = ledger.value.rows()

    /** FR-21, for real: the detail screen's delete and its undo both read back through here. */
    override suspend fun delete(transactionId: String) {
        rewrite(currentRows.filterNot { it.id == transactionId })
    }

    override suspend fun restore(transaction: Transaction) {
        rewrite(currentRows + transaction)
    }

    /**
     * Stamps the row the way the Room-backed ledger stamps one, so a screen written against this
     * meets what it will meet in the app: the three fields changed, [Transaction.editedAt] set,
     * and every other column left where it was.
     */
    override suspend fun applyEdit(transactionId: String, edit: TransactionEdit) {
        editedAt += 1
        rewrite(
            currentRows.map { row ->
                if (row.id != transactionId) {
                    row
                } else {
                    row.copy(
                        amount = edit.amount,
                        categoryId = edit.categoryId,
                        subCategoryId = edit.subCategoryId,
                        memo = edit.memo,
                        occurredAtEpochMillis = edit.occurredAtEpochMillis ?: row.occurredAtEpochMillis,
                        editedAt = editedAt,
                    )
                }
            },
        )
    }

    /** Counts up rather than reading a clock, so an assertion can name the stamp it expects. */
    private var editedAt = 0L

    /** Refuses rather than quietly succeeding: a test that writes wants a ledger that records. */
    override suspend fun record(draft: TransactionDraft): Unit =
        error("FakeLedger only reads. Use a ledger that records what it was given.")

    private fun List<Transaction>.newestFirst() =
        sortedByDescending(Transaction::occurredAtEpochMillis)

    companion object {
        /**
         * A ledger whose query has not come back — what every screen meets on a cold open, and
         * the case a bare `FakeLedger()` no longer stands for now that the seam tells them apart.
         */
        fun unanswered(): FakeLedger =
            FakeLedger().apply { ledger.value = LedgerSnapshot.Unanswered }
    }
}
