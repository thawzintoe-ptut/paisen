package com.ptut.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import com.ptut.expense.data.local.entity.TransactionEntity

/**
 * FR-22's import half. One transaction over the whole file, so a restore that dies part way adds
 * nothing rather than half a ledger — "importing the same file twice adds no rows" has to hold
 * for a first attempt that failed as much as for one that worked.
 *
 * IGNORE, never REPLACE. Both keys the table holds do the dedupe: the primary id catches the same
 * file read twice, and the F3 content index — reference, amount, instant — catches a row that
 * arrived here by import and by scan. A REPLACE would delete the row it hit, which for a row the
 * reader has since corrected means silently undoing that correction.
 */
@Dao
abstract class LedgerMergeDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIfNew(row: TransactionEntity): Long

    /**
     * @return the ids of the rows the ledger did not already hold, so the import can say which of
     * the added rows lost a category on the way in (#215).
     */
    @Transaction
    open suspend fun merge(rows: List<TransactionEntity>): Set<String> =
        rows.filter { insertIfNew(it) != IGNORED }.mapTo(mutableSetOf()) { it.id }

    private companion object {
        /** What Room returns from an IGNORE insert that hit a key. */
        const val IGNORED = -1L
    }
}
