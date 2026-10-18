package com.ptut.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ptut.expense.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    /**
     * Half-open, not `BETWEEN`: `BETWEEN` is closed at both ends, so a day boundary would need
     * a minus-one-millisecond bound and one forgotten `-1` silently double-counts midnight.
     *
     * F2: both bounds are computed in Kotlin from a Bangkok date. No SQLite date function
     * appears here, because `localtime` reads the device zone and would re-bucket the ledger
     * the moment the user travels.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE occurredAtEpochMillis >= :fromEpochMillis
          AND occurredAtEpochMillis < :untilEpochMillis
        ORDER BY occurredAtEpochMillis DESC
        """,
    )
    fun observeBetween(fromEpochMillis: Long, untilEpochMillis: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE occurredAtEpochMillis >= :fromEpochMillis
        ORDER BY occurredAtEpochMillis DESC
        """,
    )
    fun observeSince(fromEpochMillis: Long): Flow<List<TransactionEntity>>

    /**
     * The whole table, for the Ledger. Unbounded on purpose: [observeSince] is sized for the
     * reports window, and an audit list that silently stopped at a horizon would be a ledger
     * that disagrees with itself.
     */
    @Query("SELECT * FROM transactions ORDER BY occurredAtEpochMillis DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /** FR-22. Read once to write the export file rather than held open like [observeAll]. */
    @Query("SELECT * FROM transactions ORDER BY occurredAtEpochMillis DESC")
    suspend fun all(): List<TransactionEntity>

    /**
     * Aborts on a clash rather than replacing. Both keys this table holds — the primary id and the
     * F3 content index — are ones this app mints itself, so a collision is a bug in the writer and
     * `OR REPLACE` would bury it by deleting the row it hit.
     */
    @Insert
    suspend fun insert(row: TransactionEntity)

    /**
     * F3 tier two, for the importer only. Returns the new rowId, or -1 when the content index
     * already held this reference, amount and instant — a re-photograph of a slip already filed.
     *
     * IGNORE rather than ABORT because for an import a clash is the expected outcome, not a bug:
     * the run is meant to notice it, skip it, and say so in the journal.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(row: TransactionEntity): Long

    /** FR-21. Deleting an id that is already gone affects no rows, which is the right answer. */
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * The sub-category itself when it sits under [categoryId], or null. p34: a sub-category
     * belongs to one parent, so one filed under another would split the wrong total.
     */
    @Query("SELECT id FROM sub_category WHERE id = :subCategoryId AND categoryId = :categoryId")
    suspend fun subCategoryUnder(subCategoryId: String, categoryId: String): String?

    /**
     * FR-21's correction, as a targeted UPDATE rather than a whole-row `@Update`.
     *
     * The sub-category goes through [subCategoryUnder]'s lookup in the same statement, so moving a
     * row to another category drops a sub-category the new parent does not hold (#110).
     *
     * Naming the five columns is the guard. A whole-entity update would carry whatever the caller
     * happened to hold for the merchant, the reference, the source and the slip's own reading, and
     * one stale copy of those would quietly rewrite the evidence screen 11c refuses to touch.
     *
     * An id the table no longer holds updates no rows, which is the same right answer
     * [deleteById] gives.
     */
    @Query(
        """
        UPDATE transactions
        SET amountMinor = :amountMinor,
            categoryId = :categoryId,
            subCategoryId = (
                SELECT id FROM sub_category WHERE id = :subCategoryId AND categoryId = :categoryId
            ),
            memo = :memo,
            occurredAtEpochMillis = COALESCE(:occurredAtEpochMillis, occurredAtEpochMillis),
            editedAt = :editedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun applyEdit(
        id: String,
        amountMinor: Long,
        categoryId: String?,
        subCategoryId: String?,
        memo: String?,
        occurredAtEpochMillis: Long?,
        editedAtEpochMillis: Long,
    )
}

/**
 * [row] as it may be stored: its sub-category kept only when it sits under the row's category.
 * Every insert goes through here; [TransactionDao.applyEdit] does the same check in SQL.
 */
internal suspend fun TransactionDao.withCheckedSubCategory(row: TransactionEntity): TransactionEntity {
    val subCategoryId = row.subCategoryId ?: return row
    val checked = row.categoryId?.let { subCategoryUnder(subCategoryId, it) }
    return row.copy(subCategoryId = checked)
}
