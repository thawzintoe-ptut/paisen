package com.ptut.expense.domain.play

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.bucketByDay
import com.ptut.expense.domain.dateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** "At this pace" reads the last four weeks. */
private const val PACE_DAYS = 28
private const val PACE_WEEKS = 4

/** Twenty years: past this the jar says it has no date rather than one nobody can plan for. */
private const val MAX_PACE_WEEKS = 1_040L

/**
 * #104: how full the jar is and when it fills at the recent pace. [saved] is what the counted days kept
 * under their usual, each read against the day's spending now. [hasDays] is false until a day is counted.
 */
data class GoalPace(val saved: Satang, val perWeek: Satang?, val reachOn: LocalDate?, val hasDays: Boolean) {
    fun fills(target: Satang): Boolean = saved >= target
}

/**
 * What one counted day puts in the jar: its usual less its spending now, never below zero. A buy later
 * that evening shrinks it, so the jar keeps the day's final number.
 */
fun List<Transaction>.keptOn(day: GoalDay, zone: TimeZone = HomeTime): Satang {
    val usual = day.usual ?: return Satang.Zero
    val spent = spentOn(day.day, zone)
    return if (spent >= usual) Satang.Zero else usual - spent
}

fun List<Transaction>.goalPace(goal: Goal, days: List<GoalDay>, today: LocalDate, zone: TimeZone = HomeTime): GoalPace =
    pace(goal, days, today, zone, skip = null)

/** FR-92.3: the same jar if [categoryId]'s spending of the last four weeks were kept as well. */
fun List<Transaction>.goalPaceWithout(goal: Goal, days: List<GoalDay>, categoryId: String, today: LocalDate, zone: TimeZone = HomeTime): GoalPace =
    pace(goal, days, today, zone, skip = categoryId)

private fun List<Transaction>.pace(goal: Goal, days: List<GoalDay>, today: LocalDate, zone: TimeZone, skip: String?): GoalPace {
    val counted = days.filter { it.goalId == goal.id && it.counted && it.day <= today }
    val saved = Satang.ofMinorUnits(counted.sumOf { keptOn(it, zone).toMinorUnits() })
    if (counted.isEmpty()) return GoalPace(saved, perWeek = null, reachOn = null, hasDays = false)
    val since = today.minus(PACE_DAYS - 1, DateTimeUnit.DAY)
    val recent = counted.filter { it.day >= since }.sumOf { keptOn(it, zone).toMinorUnits() }
    val skipped = skip?.let { category ->
        filter { it.direction == Direction.Expense && it.categoryId == category && it.occurredAtEpochMillis.dateIn(zone) in since..today }
            .sumOf { it.amount.toMinorUnits() }
    } ?: 0L
    val perWeek = Satang.ofMinorUnits((recent + skipped) / PACE_WEEKS)
    val reachOn = when {
        saved >= goal.target -> today

        perWeek <= Satang.Zero -> null

        else -> {
            val left = goal.target.toMinorUnits() - saved.toMinorUnits()
            val weeks = (left + perWeek.toMinorUnits() - 1) / perWeek.toMinorUnits()
            // A pace that would take decades names no date; a far one would also pass LocalDate's range.
            if (weeks > MAX_PACE_WEEKS) null else today.plus(weeks.toInt(), DateTimeUnit.WEEK)
        }
    }
    return GoalPace(saved, perWeek.takeIf { it > Satang.Zero }, reachOn, hasDays = true)
}

/** Counted as [usualDay] counts a day, so the card and the jar agree. */
private fun List<Transaction>.spentOn(day: LocalDate, zone: TimeZone): Satang =
    filter { it.occurredAtEpochMillis.dateIn(zone) == day }.bucketByDay(zone).firstOrNull()?.summary?.expense ?: Satang.Zero
