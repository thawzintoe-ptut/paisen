package com.ptut.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.ptut.expense.data.local.entity.LedgerMetaEntity
import com.ptut.expense.data.local.entity.TransactionEntity

@Dao
interface LedgerMetaDao {

    /** Null until home is first written: every ledger made before #41 is baht and Bangkok. */
    @Query("SELECT * FROM ledger_meta WHERE id = 0")
    suspend fun read(): LedgerMetaEntity?

    @Upsert
    suspend fun write(meta: LedgerMetaEntity)

    @Update
    suspend fun updateRows(rows: List<TransactionEntity>)

    /**
     * #47: every row in the new home and the home itself, or neither. If the app dies halfway the
     * old ledger is still whole, and a clash on the rows' unique key leaves it whole too.
     */
    @Transaction
    suspend fun rehome(rows: List<TransactionEntity>, meta: LedgerMetaEntity) {
        updateRows(rows)
        write(meta)
    }
}
