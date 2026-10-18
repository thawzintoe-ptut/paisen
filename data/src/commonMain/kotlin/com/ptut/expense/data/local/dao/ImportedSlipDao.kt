package com.ptut.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ptut.expense.data.local.entity.ImportedSlipEntity

@Dao
interface ImportedSlipDao {
    /** F3 tier one: the same file, seen twice, must not become two transactions. */
    @Query("SELECT EXISTS(SELECT 1 FROM imported_slip WHERE hash = :hash)")
    suspend fun isImported(hash: String): Boolean

    /** Ignoring a re-insert keeps the first import's timestamp, which is the honest one. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun record(row: ImportedSlipEntity)
}
