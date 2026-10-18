package com.ptut.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ptut.expense.data.local.entity.QueuedSlipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QueuedSlipDao {
    /** Newest first: the slip the reader photographed last is the one they still remember. */
    @Query("SELECT * FROM queued_slip ORDER BY occurredAtEpochMillis DESC")
    fun observeAll(): Flow<List<QueuedSlipEntity>>

    /**
     * IGNORE on the unique slipHash: an unreadable slip has no reference and no amount, so the
     * file hash is the only thing stopping a rescan queueing the same picture twice.
     *
     * Returns the new rowId, or -1 when the hash was already queued. The caller needs the
     * difference: a run that reports queueing two slips when it wrote one row is a journal that
     * disagrees with the screen the reader is looking at.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun queue(row: QueuedSlipEntity): Long

    /**
     * The picture the queue already holds this slip under, or null when it is not queued.
     *
     * The caller needs the id and not just a yes: a rescan of the picture the queue points at
     * must keep it, while a second copy of the same slip has no evidence to be.
     */
    @Query("SELECT id FROM queued_slip WHERE slipHash = :hash LIMIT 1")
    suspend fun idHoldingSlip(hash: String): String?

    /** FR-8: the reader said this one is not worth filing, so nothing about it is kept. */
    @Query("DELETE FROM queued_slip WHERE id = :id")
    suspend fun discard(id: String)

    /** NFR-4: the images the sweep is about to drop. Read before the update, or it returns none. */
    @Query(
        "SELECT id FROM queued_slip WHERE capturedAtEpochMillis < :cutoff AND evidenceExpired = 0",
    )
    suspend fun idsWithEvidenceOlderThan(cutoff: Long): List<String>

    /**
     * The entry outlives its picture. A known spend that lost its evidence still belongs on the
     * review queue — deleting the row too would make the money disappear silently.
     */
    @Query(
        "UPDATE queued_slip SET evidenceExpired = 1 WHERE capturedAtEpochMillis < :cutoff AND evidenceExpired = 0",
    )
    suspend fun markEvidenceExpiredOlderThan(cutoff: Long)

    /** #113: an unread slip is only its picture, so once that is swept there is nothing left to check. */
    @Query("DELETE FROM queued_slip WHERE evidenceExpired = 1 AND reason = :reason")
    suspend fun dropExpired(reason: String)
}
