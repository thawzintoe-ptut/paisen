package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** #181: the last rate typed for a currency on this phone, to pre-fill the next expense. */
@Entity(tableName = "currency_rate")
data class CurrencyRateEntity(
    @PrimaryKey val currency: String,
    val rateUnscaled: Long,
    val rateScale: Int,
    val updatedAtEpochMillis: Long,
)
