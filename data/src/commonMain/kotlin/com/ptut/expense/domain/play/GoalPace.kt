package com.ptut.expense.domain.play

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
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
 * #92: how full the jar is and when it fills at the recent pace. [saved] is income less spending since
 * the goal began, never below empty. [reachOn] is null while the pace adds nothing, or with no income
 * rows, which the jar says it needs (FR-92.5).
 */
data class GoalPace(val saved: Satang, val perWeek: Satang?, val reachOn: LocalDate?, val hasIncome: Boolean) {
    fun fills(target: Satang): Boolean = saved >= target
}

fun List<Transaction>.goalPace(goal: Goal, today: LocalDate, zone: TimeZone = HomeTime): GoalPace = pace(goal, today, zone, skip = null)

/** FR-92.3: the same jar if [categoryId]'s spending were skipped from here on. */
fun List<Transaction>.goalPaceWithout(goal: Goal, categoryId: String, today: LocalDate, zone: TimeZone = HomeTime): GoalPace =
    pace(goal, today, zone, skip = categoryId)

private fun List<Transaction>.pace(goal: Goal, today: LocalDate, zone: TimeZone, skip: String?): GoalPace {
    val hasIncome = any { it.direction == Direction.Income }
    // What is already in the jar stays; a skipped habit only changes the pace from here on.
    val saved = net { it.occurredAtEpochMillis.dateIn(zone) in goal.start..today }
    val kept = if (saved < Satang.Zero) Satang.Zero else saved
    if (!hasIncome) return GoalPace(kept, perWeek = null, reachOn = null, hasIncome = false)
    val counted = filterNot { skip != null && it.direction == Direction.Expense && it.categoryId == skip }
    val since = today.minus(PACE_DAYS - 1, DateTimeUnit.DAY)
    val perWeek = Satang.ofMinorUnits(counted.net { it.occurredAtEpochMillis.dateIn(zone) in since..today }.toMinorUnits() / PACE_WEEKS)
    val reachOn = when {
        kept >= goal.target -> today

        perWeek <= Satang.Zero -> null

        else -> {
            val left = goal.target.toMinorUnits() - kept.toMinorUnits()
            val weeks = (left + perWeek.toMinorUnits() - 1) / perWeek.toMinorUnits()
            // A pace that would take decades names no date; a far one would also pass LocalDate's range.
            if (weeks > MAX_PACE_WEEKS) null else today.plus(weeks.toInt(), DateTimeUnit.WEEK)
        }
    }
    return GoalPace(kept, perWeek.takeIf { it > Satang.Zero }, reachOn, hasIncome = true)
}

private fun List<Transaction>.net(take: (Transaction) -> Boolean): Satang = Satang.ofMinorUnits(
    filter(take).sumOf { if (it.direction == Direction.Income) it.amount.toMinorUnits() else -it.amount.toMinorUnits() },
)
