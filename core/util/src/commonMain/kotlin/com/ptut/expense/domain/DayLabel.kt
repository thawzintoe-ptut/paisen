package com.ptut.expense.domain

import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.Language
import com.ptut.expense.locale.inMyanmarDigits
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Written out from the app's own day and month names rather than a locale formatter: a
 * locale-driven label would reorder the day and month on some devices, which moves the one
 * date the reader checks against their bank slips.
 */
fun LocalDate.dayLabel(): String = "${dayOfWeek.short()} ${dayAndMonth()}"

/** Without the weekday, for rows that already sit under a date heading. */
fun LocalDate.dayAndMonth(): String = "${day.inCalendarDigits()} ${month.short()}"

/** Without the month, for both ends of a range that stays inside one. */
fun LocalDate.weekdayAndDay(): String = "${dayOfWeek.short()} ${day.inCalendarDigits()}"

/** A day or a year in the app's language: ၁၆ in Burmese, the way its counts are written. */
fun Int.inCalendarDigits(): String = if (AppText.language.value == Language.Burmese) inMyanmarDigits() else toString()

/** "Oct": the month alone, for a year's bars. */
fun LocalDate.monthShort(): String = month.short()

fun Long.timeOfDayIn(zone: TimeZone): String {
    val local = Instant.fromEpochMilliseconds(this).toLocalDateTime(zone)
    return "${local.hour.pad()}:${local.minute.pad()}"
}

fun Long.dateIn(zone: TimeZone): LocalDate =
    Instant.fromEpochMilliseconds(this).dateIn(zone)

fun Instant.dateIn(zone: TimeZone): LocalDate = toLocalDateTime(zone).date

private fun Int.pad(): String = toString().padStart(2, '0')

/** "Friday", for "฿180 less than a usual Friday" (#119). */
fun DayOfWeek.longName(): String = AppText.copy.calendar.weekdays[isoDayNumber - 1]

private fun DayOfWeek.short(): String = AppText.copy.calendar.weekdaysShort[isoDayNumber - 1]

private fun Month.short(): String = AppText.copy.calendar.monthsShort[number - 1]
