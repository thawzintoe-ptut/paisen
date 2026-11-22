package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Schema 27 (J02): money that went into a jar. [id] is `slip:<reference>` for a transfer PaiSeN read, so
 * a second picture of it fills the jar once; [source] is `Slip` or `Said`; [account] the mask it went to, or null.
 */
@Entity(tableName = "play_goal_deposit", indices = [Index("goalId")])
data class PlayGoalDepositEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val amountMinor: Long,
    /** ISO `yyyy-MM-dd`. */
    val date: String,
    val atEpochMillis: Long,
    val source: String,
    val account: String? = null,
)
