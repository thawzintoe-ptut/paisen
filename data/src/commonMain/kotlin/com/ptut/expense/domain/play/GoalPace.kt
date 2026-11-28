package com.ptut.expense.domain.play

import com.ptut.expense.domain.DAYS_IN_WEEK
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.mondayOfWeekOf
import com.ptut.expense.domain.sum
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** "At this pace" reads the last four weeks. */
private const val PACE_DAYS = 28
private const val PACE_WEEKS = 4

/** Twenty years: past this the jar says it has no date rather than one nobody can plan for. */
private const val MAX_PACE_WEEKS = 1_040L

/** The third choice on Put money in: a round amount, in the home money's whole units. */
private const val ROUND_CHOICE_WHOLE = 500L

/** Put money in offers at most this many amounts (J03). */
private const val DEPOSIT_CHOICES = 3

/**
 * J02: how full the jar is and when it fills at the recent pace. [saved] is every deposit, so the jar
 * always matches the bank. [hasDeposits] is false until the first money goes in.
 */
data class GoalPace(val saved: Satang, val perWeek: Satang?, val reachOn: LocalDate?, val hasDeposits: Boolean) {
    fun fills(target: Satang): Boolean = saved >= target
}

fun List<GoalDeposit>.goalPace(goal: Goal, today: LocalDate): GoalPace {
    val mine = filter { it.goalId == goal.id && it.day <= today }
    val saved = mine.map { it.amount }.sum()
    if (mine.isEmpty()) return GoalPace(saved, perWeek = null, reachOn = null, hasDeposits = false)
    val since = today.minus(PACE_DAYS - 1, DateTimeUnit.DAY)
    val recent = mine.filter { it.day >= since }.sumOf { it.amount.toMinorUnits() }
    // A jar younger than the window divides by the weeks it has had, or its pace reads too slow.
    val daysOpen = maxOf(since, goal.start).daysUntil(today) + 1
    val weeksOpen = ((daysOpen + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK).coerceIn(1, PACE_WEEKS)
    val perWeek = Satang.ofMinorUnits(recent / weeksOpen)
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
    return GoalPace(saved, perWeek.takeIf { it > Satang.Zero }, reachOn, hasDeposits = true)
}

/** J05: what went into [goal] since Monday, for "This week: ฿220 of ฿330". */
fun List<GoalDeposit>.savedThisWeek(goal: Goal, today: LocalDate): Satang =
    filter { it.goalId == goal.id && it.day >= mondayOfWeekOf(today) && it.day <= today }.map { it.amount }.sum()

/**
 * J03's amounts, in order: today's leftover under a usual day, the weekly pace, then a round amount,
 * each once and never zero. A jar with no pace yet still gets the leftover and the round amount.
 */
fun depositChoices(leftover: Satang?, perWeek: Satang?, minorPerWhole: Long): List<Satang> =
    listOfNotNull(leftover, perWeek, Satang.ofMinorUnits(ROUND_CHOICE_WHOLE * minorPerWhole))
        .filter { it > Satang.Zero }
        .distinct()
        .take(DEPOSIT_CHOICES)
