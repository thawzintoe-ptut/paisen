package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Schema 24 (#85, #87): one day of play, keyed by its ISO `yyyy-MM-dd` in the home zone. Only what
 * the reader did is kept; the run and its freezes are worked out from these rows on every read.
 */
@Entity(tableName = "play_day")
data class PlayDayEntity(
    @PrimaryKey val date: String,
    /** The first sighting of an empty tray that day, or null. */
    val clearedAtEpochMillis: Long? = null,
    val answeredAtEpochMillis: Long? = null,
    /** The pair asked, for the 30-day rule that no pair comes back. */
    val questionKey: String? = null,
    val answeredRight: Boolean? = null,
    val pickedFirst: Boolean? = null,
)
