package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Schema 24 (#86, #91): one week of play, keyed by its Monday. Amounts in minor units. */
@Entity(tableName = "play_week")
data class PlayWeekEntity(
    @PrimaryKey val monday: String,
    /** Fixed before the reader guesses, and never changed after. */
    val paisenGuessMinor: Long? = null,
    val guessMinor: Long? = null,
    /** The week's spend at the reveal; later edits to the ledger leave the result as it was. */
    val totalMinor: Long? = null,
    val challengeCategoryId: String? = null,
    val challengeLevel: String? = null,
    val challengeLimitMinor: Long? = null,
)
