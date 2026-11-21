package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Schema 24 (#92): a goal jar. No money moves; the jar fills from what the ledger keeps. */
@Entity(tableName = "play_goal")
data class PlayGoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetMinor: Long,
    val currency: String,
    /** ISO `yyyy-MM-dd`: income less spending from this day fills the jar. */
    val startDate: String,
    val createdAtEpochMillis: Long,
    val archivedAtEpochMillis: Long? = null,
)
