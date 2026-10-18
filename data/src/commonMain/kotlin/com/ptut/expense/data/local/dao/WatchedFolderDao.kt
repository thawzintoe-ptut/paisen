package com.ptut.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.ptut.expense.data.local.entity.WatchedFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedFolderDao {

    /** Watched longest first, so the row a reader added last does not jump the list on every scan. */
    @Query("SELECT * FROM watched_folder ORDER BY name")
    fun observeAll(): Flow<List<WatchedFolderEntity>>

    @Query("SELECT * FROM watched_folder ORDER BY name")
    suspend fun all(): List<WatchedFolderEntity>

    @Upsert
    suspend fun upsert(folder: WatchedFolderEntity)

    @Delete
    suspend fun delete(folder: WatchedFolderEntity)

    @Query("UPDATE watched_folder SET watched = :watched WHERE id = :id")
    suspend fun setWatched(id: String, watched: Boolean)

    /**
     * One statement rather than read-modify-write: the scan runs on its own thread while a screen
     * may be editing the same row, and a counter that reads then writes loses one of the two.
     */
    @Query("UPDATE watched_folder SET slipsRead = slipsRead + :read WHERE id = :id")
    suspend fun addSlipsRead(id: String, read: Int)

    @Query(
        "UPDATE watched_folder SET scannedThroughSeconds = :throughSeconds, " +
            "lastCheckedAtEpochMillis = :checkedAt WHERE id = :id",
    )
    suspend fun recordCheck(id: String, throughSeconds: Long, checkedAt: Long)
}
