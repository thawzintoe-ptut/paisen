package com.ptut.expense.domain.play

import com.ptut.expense.domain.DAYS_IN_WEEK
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.inWeek
import com.ptut.expense.domain.minorPerWhole
import com.ptut.expense.domain.mondayOfWeek
import com.ptut.expense.domain.total
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private const val CHALLENGE_WEEKS = 4
private const val CHALLENGE_MIN_WEEKS = 2

/** A limit is a round figure, to the nearest ten of the home money below it. */
private const val LIMIT_STEP_WHOLE = 10L

/** How far under a usual week each level asks (#91). Always under: a challenge never asks anyone to spend. */
private val LEVEL_PERCENT = mapOf(ChallengeLevel.Easy to 95L, ChallengeLevel.Medium to 85L, ChallengeLevel.Hard to 70L)

/** How many categories p05 offers. */
const val CHALLENGE_CHOICES = 3

/**
 * #91: each level's limit for [categoryId] in the week of [monday], from the mean of up to four earlier
 * weeks. Null with fewer than two of them, or when the category spent too little to cut.
 */
fun List<Transaction>.challengeLevels(categoryId: String, monday: LocalDate, zone: TimeZone = HomeTime): Map<ChallengeLevel, Satang>? {
    val usual = usualWeek(categoryId, monday, zone) ?: return null
    val step = LIMIT_STEP_WHOLE * minorPerWhole()
    val levels = LEVEL_PERCENT.mapValues { (_, percent) -> Satang.ofMinorUnits(usual.toMinorUnits() * percent / PERCENT / step * step) }
    // A usual week too small to round leaves every level at nothing, which nobody could make.
    return levels.takeIf { it.getValue(ChallengeLevel.Hard) > Satang.Zero }
}

/** p05's choices: the categories with the most usual spending, so a challenge is about money that moves. */
fun List<Transaction>.challengeCategories(monday: LocalDate, zone: TimeZone = HomeTime): List<String> =
    filter { it.direction == Direction.Expense }.mapNotNull { it.categoryId }.distinct()
        .mapNotNull { id -> usualWeek(id, monday, zone)?.takeIf { challengeLevels(id, monday, zone) != null }?.let { id to it } }
        .sortedWith(compareByDescending<Pair<String, Satang>> { it.second }.thenBy { it.first })
        .take(CHALLENGE_CHOICES)
        .map { it.first }

/** How the week's challenge stands on [today]: spent so far, the limit, and the days left including today. */
data class ChallengeProgress(val used: Satang, val limit: Satang, val daysLeft: Int) {
    val under: Boolean get() = used <= limit
}

fun List<Transaction>.challengeProgress(challenge: Challenge, monday: LocalDate, today: LocalDate, zone: TimeZone = HomeTime): ChallengeProgress {
    val sunday = monday.plus(DAYS_IN_WEEK - 1, DateTimeUnit.DAY)
    val used = categoryWeek(challenge.categoryId, monday, zone).filter { it.occurredAtEpochMillis.dateIn(zone) <= today }.total()
    return ChallengeProgress(used, challenge.limit, (today.daysUntil(sunday) + 1).coerceIn(0, DAYS_IN_WEEK))
}

/** #91: the week ended at or under its limit. Missing it loses nothing; the next week starts fresh. */
fun List<Transaction>.challengeMet(challenge: Challenge, monday: LocalDate, zone: TimeZone = HomeTime): Boolean =
    categoryWeek(challenge.categoryId, monday, zone).total() <= challenge.limit

private fun List<Transaction>.categoryWeek(categoryId: String, monday: LocalDate, zone: TimeZone): List<Transaction> =
    filter { it.direction == Direction.Expense && it.categoryId == categoryId }.inWeek(monday, zone)

private fun List<Transaction>.usualWeek(categoryId: String, monday: LocalDate, zone: TimeZone): Satang? {
    val first = minOfOrNull { it.occurredAtEpochMillis }?.dateIn(zone)?.mondayOfWeek() ?: return null
    val weeks = (1..CHALLENGE_WEEKS).map { monday.minus(it, DateTimeUnit.WEEK) }.filter { it >= first }
    if (weeks.size < CHALLENGE_MIN_WEEKS) return null
    val usual = weeks.sumOf { categoryWeek(categoryId, it, zone).total().toMinorUnits() } / weeks.size
    return Satang.ofMinorUnits(usual).takeIf { it > Satang.Zero }
}
