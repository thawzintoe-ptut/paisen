package com.ptut.expense.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * The ranges Reports offers (#164): the week and the month to date, then 13 weeks, 26 weeks and
 * 12 months back from today. The long three end today for the reason the month does: the rest of
 * the bucket has not happened.
 */
enum class ReportPeriod { Week, Month, ThreeMonths, SixMonths, Year }

/** What one bar of the chart covers. A day for the short periods, a week, then a month for the year. */
enum class BucketSize { Day, Week, Month }

val ReportPeriod.bucketSize: BucketSize
    get() = when (this) {
        ReportPeriod.Week, ReportPeriod.Month -> BucketSize.Day
        ReportPeriod.ThreeMonths, ReportPeriod.SixMonths -> BucketSize.Week
        ReportPeriod.Year -> BucketSize.Month
    }

/**
 * Whether the period, or the one before it, reaches past the ledger's recent window (the month
 * before this one). Week and month read the recent rows, so neither loads the whole ledger.
 */
val ReportPeriod.needsWholeLedger: Boolean
    get() = this == ReportPeriod.ThreeMonths || this == ReportPeriod.SixMonths || this == ReportPeriod.Year

data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = date >= start && date <= endInclusive
}

fun ReportPeriod.rangeContaining(today: LocalDate): DateRange = when (this) {
    ReportPeriod.Week -> {
        val monday = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        DateRange(monday, monday.plus(6, DateTimeUnit.DAY))
    }

    // Ends today, not at month end: the rest of the month has not happened, and padding it
    // out would make an ordinary mid-month total look like a collapse in spending.
    ReportPeriod.Month -> DateRange(LocalDate(today.year, today.month, 1), today)

    // Whole weeks from a Monday, so every bar but the last is seven days long.
    ReportPeriod.ThreeMonths -> DateRange(today.monday().minus(LONG_WEEKS_3M - 1, DateTimeUnit.WEEK), today)

    ReportPeriod.SixMonths -> DateRange(today.monday().minus(LONG_WEEKS_6M - 1, DateTimeUnit.WEEK), today)

    ReportPeriod.Year -> DateRange(LocalDate(today.year, today.month, 1).minus(MONTHS_IN_YEAR - 1, DateTimeUnit.MONTH), today)
}

fun ReportPeriod.previousRangeBefore(today: LocalDate): DateRange = when (this) {
    ReportPeriod.Week -> rangeContaining(today).let {
        DateRange(it.start.minus(7, DateTimeUnit.DAY), it.endInclusive.minus(7, DateTimeUnit.DAY))
    }

    ReportPeriod.Month -> {
        val lastMonth = today.minus(1, DateTimeUnit.MONTH)
        DateRange(LocalDate(lastMonth.year, lastMonth.month, 1), lastMonth)
    }

    // The same stretch, one length earlier, ending on the same weekday or date.
    ReportPeriod.ThreeMonths -> rangeContaining(today).shifted(LONG_WEEKS_3M, DateTimeUnit.WEEK)

    ReportPeriod.SixMonths -> rangeContaining(today).shifted(LONG_WEEKS_6M, DateTimeUnit.WEEK)

    ReportPeriod.Year -> rangeContaining(today).shifted(1, DateTimeUnit.YEAR)
}

private fun DateRange.shifted(back: Int, unit: DateTimeUnit.DateBased) =
    DateRange(start.minus(back, unit), endInclusive.minus(back, unit))

private fun LocalDate.monday(): LocalDate = minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

private const val LONG_WEEKS_3M = 13
private const val LONG_WEEKS_6M = 26
private const val MONTHS_IN_YEAR = 12

/**
 * A month range shares one month name, so it is written once at the end. A week straddles no
 * month worth naming but does need its weekdays, because "10 – 16" alone says nothing about
 * where the week starts. A longer range names the month at both ends.
 */
fun DateRange.label(): String = when {
    start.day == 1 && start.month == endInclusive.month -> "${start.day.inCalendarDigits()} – ${endInclusive.dayAndMonth()}"

    start.plus(DAYS_IN_WEEK, DateTimeUnit.DAY) > endInclusive -> "${start.weekdayAndDay()} – ${endInclusive.weekdayAndDay()}"

    // Months apart, so each end names its own month.
    else -> "${start.dayAndMonth()} – ${endInclusive.dayAndMonth()}"
}

private const val DAYS_IN_WEEK = 7
