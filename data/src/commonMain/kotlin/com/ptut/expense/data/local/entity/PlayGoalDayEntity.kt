package com.ptut.expense.data.local.entity

import androidx.room.Entity

/**
 * Schema 26 (#104): a day the reader counted into a jar, or turned down with Not today. The usual
 * is kept as it stood, and the day's own spending is read again later, so a later buy corrects it.
 */
@Entity(tableName = "play_goal_day", primaryKeys = ["goalId", "date"])
data class PlayGoalDayEntity(
    val goalId: String,
    /** ISO `yyyy-MM-dd`. */
    val date: String,
    /** A usual day's spending when counted; null for Not today. */
    val usualMinor: Long?,
    val atEpochMillis: Long,
)
