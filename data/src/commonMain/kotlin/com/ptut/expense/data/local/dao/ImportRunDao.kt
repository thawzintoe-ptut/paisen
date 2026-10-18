package com.ptut.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ptut.expense.data.local.entity.ImportRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportRunDao {
    /**
     * F5: freshness is `now − max(finishedAt where outcome = Success)`.
     *
     * Null means no run has ever succeeded. That is a different state from a stale one, and the
     * caller must not collapse them into a zero — a fresh install would then look healthy.
     */
    @Query(
        """
        SELECT MAX(finishedAtEpochMillis) FROM import_run
        WHERE outcome = :successOutcome AND finishedAtEpochMillis IS NOT NULL
        """,
    )
    fun observeLastSuccessfulFinishedAt(successOutcome: String): Flow<Long?>

    /**
     * The journal, newest first, for the Activity screen. Bounded because the list is a log and
     * not a ledger: nobody audits a scan from six months ago, and an unbounded read of a table
     * the importer appends to every fifteen minutes grows without a reader.
     */
    @Query("SELECT * FROM import_run ORDER BY startedAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ImportRunEntity>>

    /**
     * Written when the run starts, not when it ends. A run that dies mid-scan leaves an unfinished
     * row behind, and an unfinished row is evidence; writing only on success would leave nothing
     * at all and the UI would read the last good run as still current.
     */
    @Insert
    suspend fun start(row: ImportRunEntity)

    @Update
    suspend fun finish(row: ImportRunEntity)
}
