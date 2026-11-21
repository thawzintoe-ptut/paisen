package com.ptut.expense.domain.play

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.inWeek
import com.ptut.expense.domain.mondayOfWeek
import com.ptut.expense.domain.total
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

private const val SCORE_DAYS = 30
private const val STEADY_WEEKS = 8
private const val STEADY_MIN_WEEKS = 2

/** A week within this percent of the median week is a steady one. */
private const val STEADY_PERCENT = 25L

/** What each part can give, out of 100. */
private const val KEPT_POINTS = 40
private const val SLIPS_POINTS = 30
private const val STEADY_POINTS = 30

/** #99: the three parts the reader can move. Never an investment, never a comparison with anyone. */
enum class ScorePartKind { KeptMoreThanSpent, SlipsChecked, SteadyWeeks }

/** [gain] is what the part's one action adds, the points still open in it. */
data class ScorePart(val kind: ScorePartKind, val points: Int, val of: Int, val gain: Int)

/** A part with no data is left out, and the score is scaled to the parts left (FR-99.5). */
data class MoneyScore(val total: Int, val parts: List<ScorePart>)

fun moneyScore(rows: List<Transaction>, days: List<PlayDay>, today: LocalDate, zone: TimeZone = HomeTime): MoneyScore {
    val since = today.minus(SCORE_DAYS - 1, DateTimeUnit.DAY)
    val recent = rows.filter { it.occurredAtEpochMillis.dateIn(zone) in since..today }
    val parts = listOfNotNull(kept(recent), slips(days, since, today), steady(rows, today, zone))
    val of = parts.sumOf { it.of }
    val total = if (of == 0) 0 else (parts.sumOf { it.points } * PERCENT / of).toInt()
    return MoneyScore(total, parts)
}

/** Income over spending in the last 30 days: full when income came to more, none when spending doubled it. */
private fun kept(recent: List<Transaction>): ScorePart? {
    val income = recent.filter { it.direction == Direction.Income }.total().toMinorUnits()
    if (income <= 0L) return null
    val spent = recent.filter { it.direction == Direction.Expense }.total().toMinorUnits()
    val points = when {
        spent <= income -> KEPT_POINTS
        spent >= income * 2 -> 0
        else -> (KEPT_POINTS * (income * 2 - spent) / income).toInt()
    }
    return ScorePart(ScorePartKind.KeptMoreThanSpent, points, KEPT_POINTS, KEPT_POINTS - points)
}

/** Days the tray was cleared in the last 30, while play has a day to count from. */
private fun slips(days: List<PlayDay>, since: LocalDate, today: LocalDate): ScorePart? {
    if (days.isEmpty()) return null
    val cleared = days.count { it.clearedAtEpochMillis != null && it.date in since..today }
    val points = cleared * SLIPS_POINTS / SCORE_DAYS
    return ScorePart(ScorePartKind.SlipsChecked, points, SLIPS_POINTS, SLIPS_POINTS - points)
}

/** Weeks of the last eight within a quarter of the median week. */
private fun steady(rows: List<Transaction>, today: LocalDate, zone: TimeZone): ScorePart? {
    val expenses = rows.filter { it.direction == Direction.Expense }
    val first = expenses.minOfOrNull { it.occurredAtEpochMillis }?.dateIn(zone)?.mondayOfWeek() ?: return null
    val weeks = (1..STEADY_WEEKS).map { today.mondayOfWeek().minus(it, DateTimeUnit.WEEK) }.filter { it >= first }
        .map { expenses.inWeek(it, zone).total().toMinorUnits() }
    if (weeks.size < STEADY_MIN_WEEKS) return null
    val median = weeks.sorted()[weeks.size / 2]
    val steady = weeks.count { (it - median).let { d -> if (d < 0) -d else d } * PERCENT <= median * STEADY_PERCENT }
    val points = steady * STEADY_POINTS / weeks.size
    return ScorePart(ScorePartKind.SteadyWeeks, points, STEADY_POINTS, STEADY_POINTS - points)
}
