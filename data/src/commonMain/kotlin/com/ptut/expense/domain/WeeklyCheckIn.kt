package com.ptut.expense.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** How many weeks before last week make "a usual week" (#20). */
private const val USUAL_WEEK_WEEKS = 4

/** Fewer earlier weeks than this, and nothing is called usual. */
private const val USUAL_WEEK_MIN_SAMPLES = 2

/** Top shops named in the next step. */
private const val RISE_SHOPS = 2

private const val DAYS_IN_WEEK = 7

/**
 * Monday's look back at last week (#20): what it came to beside a usual week, and the one category
 * that rose most. From the reader's own rows on the phone, never a budget, and never a warning.
 */
data class WeeklyCheckIn(
    val from: LocalDate,
    val to: LocalDate,
    val spent: Satang,
    /** The mean of up to four earlier weeks, or null with fewer than two of them in the ledger. */
    val usual: Satang?,
    /** The category that rose most over its usual week, or null when none rose. */
    val rise: CategoryRise?,
) {
    val difference: Satang? get() = usual?.let { spent - it }
}

/**
 * [shops] are the counterparties that took most of it last week, biggest first. #22: [difference]
 * counts only what was not marked planned; [planned] is what was, in the same category.
 */
data class CategoryRise(val categoryId: String, val difference: Satang, val shops: List<String>, val planned: Satang = Satang.Zero)

/**
 * Last week, Monday to Sunday on [zone] days, seen from [today]. Null on a week with no spending,
 * so a quiet week draws no card at all.
 */
fun List<Transaction>.weeklyCheckIn(today: LocalDate, zone: TimeZone = BangkokTime): WeeklyCheckIn? {
    val thisMonday = today.mondayOfWeek()
    val from = thisMonday.minus(DAYS_IN_WEEK, DateTimeUnit.DAY)
    val expenses = filter { it.direction == Direction.Expense }
    val lastWeek = expenses.inWeek(from, zone)
    val spent = lastWeek.total()
    if (spent <= Satang.Zero) return null

    val firstMonday = minOf { it.occurredAtEpochMillis }.dateIn(zone).mondayOfWeek()
    val earlier = (1..USUAL_WEEK_WEEKS)
        .map { from.minus(it, DateTimeUnit.WEEK) }
        .filter { it >= firstMonday }
        .map { expenses.inWeek(it, zone) }
    if (earlier.size < USUAL_WEEK_MIN_SAMPLES) {
        return WeeklyCheckIn(from, from.plus(DAYS_IN_WEEK - 1, DateTimeUnit.DAY), spent, usual = null, rise = null)
    }

    return WeeklyCheckIn(
        from = from,
        to = from.plus(DAYS_IN_WEEK - 1, DateTimeUnit.DAY),
        spent = spent,
        usual = earlier.map { it.total() }.mean(),
        rise = lastWeek.biggestRise(earlier),
    )
}

internal fun List<Transaction>.inWeek(monday: LocalDate, zone: TimeZone): List<Transaction> {
    val end = monday.plus(DAYS_IN_WEEK, DateTimeUnit.DAY)
    return filter { it.occurredAtEpochMillis.dateIn(zone).let { day -> day >= monday && day < end } }
}

internal fun List<Transaction>.total(): Satang = fold(Satang.Zero) { sum, row -> sum + row.amount }

internal fun List<Satang>.mean(): Satang = Satang(sumOf { it.raw } / size)

/**
 * A row with no category cannot be named, so it never leads the next step. #22: a planned payment is
 * never part of a rise, this week or in the usual weeks it is measured against.
 */
private fun List<Transaction>.biggestRise(earlier: List<List<Transaction>>): CategoryRise? {
    val unplanned = filterNot { it.planned }
    return unplanned.mapNotNull { it.categoryId }.distinct()
        .map { id ->
            val usual = earlier.map { week -> week.filter { it.categoryId == id && !it.planned }.total() }.mean()
            id to (unplanned.filter { it.categoryId == id }.total() - usual)
        }
        .filter { (_, difference) -> difference > Satang.Zero }
        .maxByOrNull { (_, difference) -> difference }
        ?.let { (id, difference) ->
            CategoryRise(id, difference, unplanned.shopsIn(id), planned = filter { it.categoryId == id && it.planned }.total())
        }
}

private fun List<Transaction>.shopsIn(categoryId: String): List<String> =
    filter { it.categoryId == categoryId }
        .mapNotNull { row -> row.counterparty?.trim()?.takeIf { it.isNotEmpty() }?.let { it to row.amount } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, amounts) -> amounts.fold(Satang.Zero) { sum, amount -> sum + amount } }
        .entries.sortedByDescending { it.value }
        .take(RISE_SHOPS)
        .map { it.key }

/** The Monday that starts [this] date's week. PaiSeN's weeks run Monday to Sunday. */
internal fun LocalDate.mondayOfWeek(): LocalDate = minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
