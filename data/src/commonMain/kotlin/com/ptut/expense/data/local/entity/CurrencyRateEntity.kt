package com.ptut.expense.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * #181: the last rate typed for a currency on this phone, to pre-fill the next entry. #44: a rate is
 * [home] per one unit of [currency], so a rate saved against baht is never used against kyat.
 */
@Entity(tableName = "currency_rate", primaryKeys = ["currency", "home"])
data class CurrencyRateEntity(
    val currency: String,
    val rateUnscaled: Long,
    val rateScale: Int,
    val updatedAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "THB") val home: String = "THB",
)
