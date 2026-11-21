package com.ptut.expense.data.repository

import com.ptut.expense.data.local.dao.PlayDao
import com.ptut.expense.data.local.entity.PlayDayEntity
import com.ptut.expense.data.local.entity.PlayGoalDayEntity
import com.ptut.expense.data.local.entity.PlayGoalEntity
import com.ptut.expense.data.local.entity.PlayMonthEntity
import com.ptut.expense.data.local.entity.PlayStampEntity
import com.ptut.expense.data.local.entity.PlayWeekEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The play tables in memory, under [PlayDao]'s own transaction bodies, so the once-only rules are
 * tested as they ship. The host JVM has no SQLite driver; the migration runs on the simulator.
 */
internal class PlayTables : PlayDao() {

    val dayRows = MutableStateFlow(mapOf<String, PlayDayEntity>())
    val weekRows = MutableStateFlow(mapOf<String, PlayWeekEntity>())
    val monthRows = MutableStateFlow(mapOf<String, PlayMonthEntity>())
    val stampRows = MutableStateFlow(mapOf<String, PlayStampEntity>())
    val goalRows = MutableStateFlow(mapOf<String, PlayGoalEntity>())
    val goalDayRows = MutableStateFlow(mapOf<Pair<String, String>, PlayGoalDayEntity>())

    override fun observeDays(): Flow<List<PlayDayEntity>> = dayRows.map { it.values.sortedBy(PlayDayEntity::date) }

    override fun observeWeeks(): Flow<List<PlayWeekEntity>> = weekRows.map { it.values.sortedBy(PlayWeekEntity::monday) }

    override fun observeMonths(): Flow<List<PlayMonthEntity>> = monthRows.map { it.values.sortedBy(PlayMonthEntity::month) }

    override fun observeStamps(): Flow<List<PlayStampEntity>> = stampRows.map { rows -> rows.values.sortedWith(compareBy({ it.earnedOn }, { it.id })) }

    override fun observeGoals(): Flow<List<PlayGoalEntity>> = goalRows.map { it.values.sortedBy(PlayGoalEntity::createdAtEpochMillis) }

    override fun observeGoalDays(): Flow<List<PlayGoalDayEntity>> = goalDayRows.map { it.values.sortedBy(PlayGoalDayEntity::date) }

    override suspend fun day(date: String): PlayDayEntity? = dayRows.value[date]

    override suspend fun week(monday: String): PlayWeekEntity? = weekRows.value[monday]

    override suspend fun month(month: String): PlayMonthEntity? = monthRows.value[month]

    override suspend fun writeDay(day: PlayDayEntity) = dayRows.update { it + (day.date to day) }

    override suspend fun writeWeek(week: PlayWeekEntity) = weekRows.update { it + (week.monday to week) }

    override suspend fun writeMonth(month: PlayMonthEntity) = monthRows.update { it + (month.month to month) }

    override suspend fun earn(stamp: PlayStampEntity): Long {
        if (stamp.id in stampRows.value) return -1
        stampRows.update { it + (stamp.id to stamp) }
        return stampRows.value.size.toLong()
    }

    override suspend fun reveal(id: String, atEpochMillis: Long) = stampRows.update { rows ->
        val row = rows[id] ?: return@update rows
        if (row.revealedAtEpochMillis != null) rows else rows + (id to row.copy(revealedAtEpochMillis = atEpochMillis))
    }

    override suspend fun writeGoal(goal: PlayGoalEntity) = goalRows.update { it + (goal.id to goal) }

    override suspend fun archiveGoal(id: String, atEpochMillis: Long) = goalRows.update { rows ->
        val row = rows[id] ?: return@update rows
        if (row.archivedAtEpochMillis != null) rows else rows + (id to row.copy(archivedAtEpochMillis = atEpochMillis))
    }

    override suspend fun allDays(): List<PlayDayEntity> = dayRows.value.values.toList()

    override suspend fun allWeeks(): List<PlayWeekEntity> = weekRows.value.values.toList()

    override suspend fun allMonths(): List<PlayMonthEntity> = monthRows.value.values.toList()

    override suspend fun allStamps(): List<PlayStampEntity> = stampRows.value.values.toList()

    override suspend fun allGoals(): List<PlayGoalEntity> = goalRows.value.values.toList()

    override suspend fun allGoalDays(): List<PlayGoalDayEntity> = goalDayRows.value.values.toList()

    override suspend fun countDay(day: PlayGoalDayEntity) = goalDayRows.update { held ->
        val key = day.goalId to day.date
        if (key in held) held else held + (key to day)
    }

    override suspend fun restoreGoalDays(rows: List<PlayGoalDayEntity>) = goalDayRows.update { held ->
        held + rows.map { (it.goalId to it.date) to it }.filterNot { it.first in held }
    }

    // Days, weeks and months upsert, as Room's @Upsert does; the DAO has already merged each row.
    override suspend fun restoreDays(rows: List<PlayDayEntity>) = dayRows.update { held -> held + rows.associateBy { it.date } }

    override suspend fun restoreWeeks(rows: List<PlayWeekEntity>) = weekRows.update { held -> held + rows.associateBy { it.monday } }

    override suspend fun restoreMonths(rows: List<PlayMonthEntity>) = monthRows.update { held -> held + rows.associateBy { it.month } }

    override suspend fun restoreStamps(rows: List<PlayStampEntity>) = stampRows.update { held -> held + rows.filterNot { it.id in held }.associateBy { it.id } }

    override suspend fun restoreGoals(rows: List<PlayGoalEntity>) = goalRows.update { held -> held + rows.filterNot { it.id in held }.associateBy { it.id } }
}
