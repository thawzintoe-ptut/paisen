package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Schema 24 (#88): one stamp. [id] is `<kind>:<ref>`, so earning the same thing twice keeps one row. */
@Entity(tableName = "play_stamp", indices = [Index("earnedOn")])
data class PlayStampEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val ref: String,
    val earnedOn: String,
    /** Null while the card is face down. */
    val revealedAtEpochMillis: Long? = null,
)
