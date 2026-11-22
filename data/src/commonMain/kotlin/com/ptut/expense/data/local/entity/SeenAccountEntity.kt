package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Schema 27 (J04): an account of the reader's own that a slip moved money to, with how often. */
@Entity(tableName = "seen_account")
data class SeenAccountEntity(
    @PrimaryKey val mask: String,
    val holder: String,
    val seenCount: Int,
    val lastSeenEpochMillis: Long,
)
