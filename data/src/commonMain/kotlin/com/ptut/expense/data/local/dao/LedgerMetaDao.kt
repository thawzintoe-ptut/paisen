package com.ptut.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.ptut.expense.data.local.entity.LedgerMetaEntity
import com.ptut.expense.data.local.entity.TransactionEntity

/** #41: the one row that says what money the ledger counts in and where its days start. */
@Dao
abstract class LedgerMetaDao {

    /** Null until home is first written: every ledger made before #41 is baht and Bangkok. */
    @Query("SELECT * FROM ledger_meta WHERE id = 0")
    abstract suspend fun read(): LedgerMetaEntity?

    @Upsert
    abstract suspend fun write(meta: LedgerMetaEntity)

    @Query("SELECT COUNT(*) FROM transactions")
    protected abstract suspend fun rowCount(): Long

    @Update
    protected abstract suspend fun updateRows(rows: List<TransactionEntity>)

    /**
     * #41: home changes freely only while the ledger is empty. The count and the write are one
     * transaction, so a slip the importer files in between cannot land in the old money.
     */
    @Transaction
    open suspend fun writeIfEmpty(meta: LedgerMetaEntity): Boolean {
        if (rowCount() > 0) return false
        write(meta)
        return true
    }

    /**
     * #47: every row in the new home and the home itself, or neither. If the app dies halfway the
     * old ledger is still whole, and a clash on the rows' unique key leaves it whole too.
     */
    @Transaction
    open suspend fun rehome(rows: List<TransactionEntity>, meta: LedgerMetaEntity) {
        updateRows(rows)
        write(meta)
    }
}
