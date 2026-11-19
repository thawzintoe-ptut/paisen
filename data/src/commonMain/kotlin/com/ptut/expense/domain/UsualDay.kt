package com.ptut.expense.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/** How many earlier same weekdays make "a usual Friday": eight, about two months (#119). */
private const val USUAL_DAY_WEEKS = 8

/** Fewer earlier same weekdays than this, and nothing is called usual. */
private const val USUAL_DAY_MIN_SAMPLES = 3

/** One day's spending beside a usual day of the same weekday (#119). Against the reader's own history, never a budget. */
data class UsualDay(
    val day: LocalDate,
    val spent: Satang,
    val usual: Satang,
) {
    val difference: Satang get() = spent - usual
}

/**
 * The median spending of the last [USUAL_DAY_WEEKS] same weekdays before [day], on [zone] days. The
 * median, not the mean, so one big shop does not move "a usual Friday". A weekday with no spending
 * counts as ฿0, but only from the ledger's first row on. Null below [USUAL_DAY_MIN_SAMPLES].
 */
fun List<Transaction>.usualDay(day: LocalDate, zone: TimeZone = HomeTime): UsualDay? {
    val first = minOfOrNull { it.occurredAtEpochMillis }?.dateIn(zone) ?: return null
    val spentOn = bucketByDay(zone).associate { it.date to it.summary.expense }
    val samples = (1..USUAL_DAY_WEEKS)
        .map { day.minus(it, DateTimeUnit.WEEK) }
        .filter { it >= first }
    if (samples.size < USUAL_DAY_MIN_SAMPLES) return null

    val sorted = samples.map { spentOn[it] ?: Satang.Zero }.sorted()
    val middle = sorted.size / 2
    val usual = if (sorted.size % 2 == 1) {
        sorted[middle]
    } else {
        Satang((sorted[middle - 1].raw + sorted[middle].raw) / 2)
    }
    return UsualDay(day = day, spent = spentOn[day] ?: Satang.Zero, usual = usual)
}
