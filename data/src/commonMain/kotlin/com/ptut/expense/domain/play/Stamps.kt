package com.ptut.expense.domain.play

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.dateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/** From this hour on, a cleared tray is a Night owl. */
private const val NIGHT_FROM_HOUR = 22

/** Before this hour, a cleared tray is an Early bird. */
private const val EARLY_UNTIL_HOUR = 8

/** This many slips filed in one day is a Big sweep. */
const val BIG_SWEEP_SLIPS = 10

/**
 * #88: the stamp a cleared tray earns. Rare ones come from what the reader did, never from chance
 * (D3): ten slips in a day, then a late clear, then an early one; any other day earns its weekday.
 */
fun stampFor(clearedAt: LocalDateTime, slipsFiledThatDay: Int): StampKind = when {
    slipsFiledThatDay >= BIG_SWEEP_SLIPS -> StampKind.BigSweep
    clearedAt.hour >= NIGHT_FROM_HOUR -> StampKind.NightOwl
    clearedAt.hour < EARLY_UNTIL_HOUR -> StampKind.EarlyBird
    else -> clearedAt.date.dayOfWeek.stamp()
}

private fun DayOfWeek.stamp(): StampKind = when (this) {
    DayOfWeek.MONDAY -> StampKind.Monday
    DayOfWeek.TUESDAY -> StampKind.Tuesday
    DayOfWeek.WEDNESDAY -> StampKind.Wednesday
    DayOfWeek.THURSDAY -> StampKind.Thursday
    DayOfWeek.FRIDAY -> StampKind.Friday
    DayOfWeek.SATURDAY -> StampKind.Saturday
    DayOfWeek.SUNDAY -> StampKind.Sunday
}

/** Slips the reader filed on [day], counted by when each was filed rather than when it was paid. */
fun List<Transaction>.slipsFiledOn(day: LocalDate, zone: TimeZone = HomeTime): Int =
    count { row -> row.filedAt?.dateIn(zone) == day }

/** How far the reader is through one set (p04). */
data class SetProgress(val set: StampSet, val have: Int, val of: Int) {
    val complete: Boolean get() = have >= of
}

/** Each set's kinds earned at least once, in p04's order. */
fun List<Stamp>.setProgress(): List<SetProgress> = StampSet.entries.map { set ->
    val kinds = StampKind.entries.filter { it.set == set }
    SetProgress(set, have = kinds.count { kind -> any { it.kind == kind } }, of = kinds.size)
}

/**
 * p04's calendar: days of [month] (ISO `yyyy-MM`) up to [today] with no money going out. Days before
 * the ledger's first row are not counted, since nothing could have been spent there that it knows.
 */
fun List<Transaction>.noSpendDays(month: String, today: LocalDate, zone: TimeZone = HomeTime): List<LocalDate> {
    val first = minOfOrNull { it.occurredAtEpochMillis }?.dateIn(zone) ?: return emptyList()
    val yearMonth = month.toYearMonthOrNull() ?: return emptyList()
    val spent = filter { it.direction == Direction.Expense }.map { it.occurredAtEpochMillis.dateIn(zone) }.toSet()
    val days = generateSequence(yearMonth.firstDay) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= yearMonth.lastDay }
    return days.filter { it in first..today && it !in spent }.toList()
}
