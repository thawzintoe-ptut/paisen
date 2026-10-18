package com.ptut.expense.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/** One day of the chart: the Bangkok date and what was spent on it. */
data class DaySpend(val date: LocalDate, val spent: Satang)

/**
 * FR-17. Every day in [range], including the ones with nothing on them.
 *
 * Days with no spending are present and zero rather than absent, because the chart draws seven
 * columns whatever happened — a week that dropped its quiet days would silently rescale.
 *
 * Income is excluded, so this sums to the same figure [summarise] calls `expense`. That equality
 * is the chart agreeing with the total above it, and it has a test of its own: the two are the
 * same rows read twice, and a chart that disagrees with its own hero is the aggregation being
 * wrong somewhere the reader cannot see.
 */
fun List<Transaction>.spendPerDay(range: DateRange, zone: TimeZone = BangkokTime): List<DaySpend> {
    val spentOn = filter { it.direction == Direction.Expense }
        .groupBy { it.occurredAtEpochMillis.dateIn(zone) }

    return generateSequence(range.start) { it.plus(1, DateTimeUnit.DAY) }
        .takeWhile { it <= range.endInclusive }
        .map { date -> DaySpend(date, spentOn[date].orEmpty().map { it.amount }.sum()) }
        .toList()
}
