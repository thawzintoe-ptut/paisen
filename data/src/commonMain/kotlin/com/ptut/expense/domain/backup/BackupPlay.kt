package com.ptut.expense.domain.backup

import kotlinx.serialization.Serializable

/**
 * Format 5 (#82): play's facts, so a restored phone keeps its run, stamps and goals. Each class
 * mirrors its table's columns, as [BackupCategory] mirrors `category`. Reactions are left out:
 * they sync with their group.
 */
@Serializable
data class BackupPlay(
    val days: List<BackupPlayDay> = emptyList(),
    val weeks: List<BackupPlayWeek> = emptyList(),
    val months: List<BackupPlayMonth> = emptyList(),
    val stamps: List<BackupStamp> = emptyList(),
    val goals: List<BackupGoal> = emptyList(),
    /** Format 6 (#104): days counted into a jar, and days turned down. Absent in format 5. */
    val goalDays: List<BackupGoalDay> = emptyList(),
)

@Serializable
data class BackupPlayDay(
    val date: String,
    val clearedAtEpochMillis: Long? = null,
    val answeredAtEpochMillis: Long? = null,
    val questionKey: String? = null,
    val answeredRight: Boolean? = null,
    val pickedFirst: Boolean? = null,
)

@Serializable
data class BackupPlayWeek(
    val monday: String,
    val paisenGuessMinor: Long? = null,
    val guessMinor: Long? = null,
    val totalMinor: Long? = null,
    val challengeCategoryId: String? = null,
    val challengeLevel: String? = null,
    val challengeLimitMinor: Long? = null,
)

@Serializable
data class BackupPlayMonth(val month: String, val storySeenAtEpochMillis: Long? = null, val sealedAtEpochMillis: Long? = null)

@Serializable
data class BackupStamp(val id: String, val kind: String, val ref: String, val earnedOn: String, val revealedAtEpochMillis: Long? = null)

@Serializable
data class BackupGoal(
    val id: String,
    val name: String,
    val targetMinor: Long,
    val currency: String,
    val startDate: String,
    val createdAtEpochMillis: Long,
    val archivedAtEpochMillis: Long? = null,
)

@Serializable
data class BackupGoalDay(val goalId: String, val date: String, val usualMinor: Long? = null, val atEpochMillis: Long)
