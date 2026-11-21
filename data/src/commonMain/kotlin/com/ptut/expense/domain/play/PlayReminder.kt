package com.ptut.expense.domain.play

import com.ptut.expense.domain.HomeTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** D15: the weekly play reminder's hour, in home time. */
private const val REMIND_HOUR = 9

/** D15: the next Monday 09:00 in [zone] after [now]; on a Monday before nine, that same morning. */
fun nextPlayReminderAt(now: Instant, zone: TimeZone = HomeTime): Instant {
    val local = now.toLocalDateTime(zone)
    val nine = LocalTime(REMIND_HOUR, 0)
    var day = local.date
    while (day.dayOfWeek != DayOfWeek.MONDAY || day.atTime(nine) <= local) day = day.plus(1, DateTimeUnit.DAY)
    return day.atTime(nine).toInstant(zone)
}

/** The week a Monday reminder asks the reader to guess: the one that just ended. */
fun guessWeekFor(reminderMonday: LocalDate): LocalDate = reminderMonday.minus(1, DateTimeUnit.WEEK)
