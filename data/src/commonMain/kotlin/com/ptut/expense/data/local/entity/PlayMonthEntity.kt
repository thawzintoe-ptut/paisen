package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Schema 24 (#90, #98): one month of play, keyed by ISO `yyyy-MM`. */
@Entity(tableName = "play_month")
data class PlayMonthEntity(
    @PrimaryKey val month: String,
    val storySeenAtEpochMillis: Long? = null,
    val sealedAtEpochMillis: Long? = null,
)
