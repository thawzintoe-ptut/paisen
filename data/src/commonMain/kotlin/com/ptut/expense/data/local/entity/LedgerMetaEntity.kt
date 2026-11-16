package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * #41: the ledger's home currency and day zone, one row. It lives beside the rows it describes so
 * #47 can change both in one transaction; the settings store keeps a copy only for a fast launch.
 */
@Entity(tableName = "ledger_meta")
data class LedgerMetaEntity(
    @PrimaryKey val id: Int = ONLY,
    val currency: String,
    val zone: String,
) {
    companion object {
        const val ONLY = 0
    }
}
