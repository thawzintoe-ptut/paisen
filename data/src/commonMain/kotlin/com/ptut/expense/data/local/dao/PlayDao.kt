package com.ptut.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ptut.expense.data.local.entity.PlayDayEntity
import com.ptut.expense.data.local.entity.PlayGoalEntity
import com.ptut.expense.data.local.entity.PlayMonthEntity
import com.ptut.expense.data.local.entity.PlayStampEntity
import com.ptut.expense.data.local.entity.PlayWeekEntity
import kotlinx.coroutines.flow.Flow

/**
 * #82: play's facts. Every write that earns a stamp is one transaction with the stamp's fixed id,
 * so a second tap, a second watcher or a crash halfway never earns twice.
 */
@Dao
abstract class PlayDao {

    @Query("SELECT * FROM play_day ORDER BY date")
    abstract fun observeDays(): Flow<List<PlayDayEntity>>

    @Query("SELECT * FROM play_week ORDER BY monday")
    abstract fun observeWeeks(): Flow<List<PlayWeekEntity>>

    @Query("SELECT * FROM play_month ORDER BY month")
    abstract fun observeMonths(): Flow<List<PlayMonthEntity>>

    @Query("SELECT * FROM play_stamp ORDER BY earnedOn, id")
    abstract fun observeStamps(): Flow<List<PlayStampEntity>>

    @Query("SELECT * FROM play_goal ORDER BY createdAtEpochMillis")
    abstract fun observeGoals(): Flow<List<PlayGoalEntity>>

    @Query("SELECT * FROM play_day WHERE date = :date")
    protected abstract suspend fun day(date: String): PlayDayEntity?

    @Query("SELECT * FROM play_week WHERE monday = :monday")
    protected abstract suspend fun week(monday: String): PlayWeekEntity?

    @Query("SELECT * FROM play_month WHERE month = :month")
    protected abstract suspend fun month(month: String): PlayMonthEntity?

    @Upsert
    protected abstract suspend fun writeDay(day: PlayDayEntity)

    @Upsert
    protected abstract suspend fun writeWeek(week: PlayWeekEntity)

    @Upsert
    protected abstract suspend fun writeMonth(month: PlayMonthEntity)

    /** -1 when a stamp with this id is there already. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun earn(stamp: PlayStampEntity): Long

    /** #85: the day row and its stamp, or neither. The first sighting of an empty tray wins. */
    @Transaction
    open suspend fun clear(date: String, atEpochMillis: Long, stamp: PlayStampEntity): Boolean {
        val day = day(date)
        if (day?.clearedAtEpochMillis != null) return false
        writeDay((day ?: PlayDayEntity(date)).copy(clearedAtEpochMillis = atEpochMillis))
        earn(stamp)
        return true
    }

    /** #87: the first answer of the day stands. */
    @Transaction
    open suspend fun answer(date: String, key: String, right: Boolean, pickedFirst: Boolean, atEpochMillis: Long) {
        val day = day(date)
        if (day?.answeredAtEpochMillis != null) return
        writeDay((day ?: PlayDayEntity(date)).copy(answeredAtEpochMillis = atEpochMillis, questionKey = key, answeredRight = right, pickedFirst = pickedFirst))
    }

    /** #86: PaiSeN's guess is written once, before the reader's; the stored one is the answer. */
    @Transaction
    open suspend fun fixPaisenGuess(monday: String, minor: Long): Long {
        val week = week(monday)
        week?.paisenGuessMinor?.let { return it }
        writeWeek((week ?: PlayWeekEntity(monday)).copy(paisenGuessMinor = minor))
        return minor
    }

    /** #86: the reader's guess and the total are kept once, with the stamp when the reader was closer. */
    @Transaction
    open suspend fun revealGuess(monday: String, guess: Long, total: Long, stamp: PlayStampEntity?) {
        val week = week(monday) ?: PlayWeekEntity(monday)
        if (week.guessMinor != null) return
        writeWeek(week.copy(guessMinor = guess, totalMinor = total))
        stamp?.let { earn(it) }
    }

    /** #91: a second pick the same week replaces the first. */
    @Transaction
    open suspend fun pickChallenge(monday: String, categoryId: String, level: String, limitMinor: Long) {
        val week = week(monday) ?: PlayWeekEntity(monday)
        writeWeek(week.copy(challengeCategoryId = categoryId, challengeLevel = level, challengeLimitMinor = limitMinor))
    }

    /** #98: the seal and its stamp, once. */
    @Transaction
    open suspend fun seal(month: String, atEpochMillis: Long, stamp: PlayStampEntity) {
        val row = month(month)
        if (row?.sealedAtEpochMillis != null) return
        writeMonth((row ?: PlayMonthEntity(month)).copy(sealedAtEpochMillis = atEpochMillis))
        earn(stamp)
    }

    @Transaction
    open suspend fun storySeen(month: String, atEpochMillis: Long) {
        val row = month(month)
        if (row?.storySeenAtEpochMillis != null) return
        writeMonth((row ?: PlayMonthEntity(month)).copy(storySeenAtEpochMillis = atEpochMillis))
    }

    @Query("UPDATE play_stamp SET revealedAtEpochMillis = :atEpochMillis WHERE id = :id AND revealedAtEpochMillis IS NULL")
    abstract suspend fun reveal(id: String, atEpochMillis: Long)

    @Upsert
    abstract suspend fun writeGoal(goal: PlayGoalEntity)

    @Query("UPDATE play_goal SET archivedAtEpochMillis = :atEpochMillis WHERE id = :id AND archivedAtEpochMillis IS NULL")
    abstract suspend fun archiveGoal(id: String, atEpochMillis: Long)

    // Backup: every row out, and back in merged column by column, the phone's own value first.

    @Query("SELECT * FROM play_day")
    abstract suspend fun allDays(): List<PlayDayEntity>

    @Query("SELECT * FROM play_week")
    abstract suspend fun allWeeks(): List<PlayWeekEntity>

    @Query("SELECT * FROM play_month")
    abstract suspend fun allMonths(): List<PlayMonthEntity>

    @Query("SELECT * FROM play_stamp")
    abstract suspend fun allStamps(): List<PlayStampEntity>

    @Query("SELECT * FROM play_goal")
    abstract suspend fun allGoals(): List<PlayGoalEntity>

    @Upsert
    protected abstract suspend fun restoreDays(rows: List<PlayDayEntity>)

    @Upsert
    protected abstract suspend fun restoreWeeks(rows: List<PlayWeekEntity>)

    @Upsert
    protected abstract suspend fun restoreMonths(rows: List<PlayMonthEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun restoreStamps(rows: List<PlayStampEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun restoreGoals(rows: List<PlayGoalEntity>)

    /**
     * A backup's play in one transaction, so a file read halfway leaves nothing half restored. A day,
     * week or month the phone already holds keeps every fact it has and takes the backup's for the rest:
     * a fresh install marks today before Import runs, and that row must not hide the backup's answer.
     */
    @Transaction
    open suspend fun restore(
        days: List<PlayDayEntity>,
        weeks: List<PlayWeekEntity>,
        months: List<PlayMonthEntity>,
        stamps: List<PlayStampEntity>,
        goals: List<PlayGoalEntity>,
    ) {
        val haveDays = allDays().associateBy { it.date }
        restoreDays(days.map { row -> haveDays[row.date]?.filledFrom(row) ?: row })
        val haveWeeks = allWeeks().associateBy { it.monday }
        restoreWeeks(weeks.map { row -> haveWeeks[row.monday]?.filledFrom(row) ?: row })
        val haveMonths = allMonths().associateBy { it.month }
        restoreMonths(months.map { row -> haveMonths[row.month]?.filledFrom(row) ?: row })
        restoreStamps(stamps)
        restoreGoals(goals)
    }
}

private fun PlayDayEntity.filledFrom(other: PlayDayEntity) = PlayDayEntity(
    date = date,
    clearedAtEpochMillis = clearedAtEpochMillis ?: other.clearedAtEpochMillis,
    answeredAtEpochMillis = answeredAtEpochMillis ?: other.answeredAtEpochMillis,
    // The question's three columns travel together, so the pick never names another day's pair.
    questionKey = if (answeredAtEpochMillis != null) questionKey else other.questionKey,
    answeredRight = if (answeredAtEpochMillis != null) answeredRight else other.answeredRight,
    pickedFirst = if (answeredAtEpochMillis != null) pickedFirst else other.pickedFirst,
)

private fun PlayWeekEntity.filledFrom(other: PlayWeekEntity) = PlayWeekEntity(
    monday = monday,
    paisenGuessMinor = paisenGuessMinor ?: other.paisenGuessMinor,
    guessMinor = guessMinor ?: other.guessMinor,
    totalMinor = totalMinor ?: other.totalMinor,
    challengeCategoryId = challengeCategoryId ?: other.challengeCategoryId,
    challengeLevel = challengeLevel ?: other.challengeLevel,
    challengeLimitMinor = challengeLimitMinor ?: other.challengeLimitMinor,
)

private fun PlayMonthEntity.filledFrom(other: PlayMonthEntity) = PlayMonthEntity(
    month = month,
    storySeenAtEpochMillis = storySeenAtEpochMillis ?: other.storySeenAtEpochMillis,
    sealedAtEpochMillis = sealedAtEpochMillis ?: other.sealedAtEpochMillis,
)
