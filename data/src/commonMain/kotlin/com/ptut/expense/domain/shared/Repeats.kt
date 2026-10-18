package com.ptut.expense.domain.shared

import com.ptut.expense.locale.AppText
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/** p33's "How often". */
enum class RepeatFrequency { Weekly, TwoWeeks, Monthly, Yearly }

/** p33's type chips: each sets a name and an icon. */
enum class RepeatKind { Rent, Utilities, Internet, Subscription }

/** "Ends": never, on a date, or after a number of times. */
sealed interface RepeatEnd {
    data object Never : RepeatEnd

    data class OnDate(val last: LocalDate) : RepeatEnd

    data class After(val times: Int) : RepeatEnd
}

/**
 * #182: an expense that adds itself. [start] is the first date and the anchor: a monthly repeat
 * from the 31st lands on each month's last day when the month is shorter, and goes back to the
 * 31st when it is long enough (a yearly one from 29 Feb lands on 28 Feb outside leap years).
 *
 * [lastAdded] is the newest date already in the log; catching up starts after it, so a date is
 * added once. A change to the template applies from the next date only: what was added stays.
 */
data class RepeatRule(
    val id: String,
    val groupId: String,
    val kind: RepeatKind,
    val frequency: RepeatFrequency,
    val start: LocalDate,
    val end: RepeatEnd,
    val remind: Boolean,
    val active: Boolean,
    val lastAdded: LocalDate?,
    val description: String,
    val categoryId: String?,
    val currency: String,
    val amountMinor: Long,
    val rate: Rate?,
    val payerMemberId: String,
    val splitMode: SplitMode,
    /** Member id to weight, in the mode's own unit, as Add expense saves them. */
    val weights: Map<String, Long>,
    val createdAtEpochMillis: Long,
)

/** The [index]th date of the repeat, counting [RepeatRule.start] as 0. */
fun RepeatRule.occurrence(index: Int): LocalDate = when (frequency) {
    RepeatFrequency.Weekly -> start.plus(DatePeriod(days = WEEK * index))
    RepeatFrequency.TwoWeeks -> start.plus(DatePeriod(days = WEEK * 2 * index))
    RepeatFrequency.Monthly -> start.monthsLater(index)
    RepeatFrequency.Yearly -> start.monthsLater(MONTHS_PER_YEAR * index)
}

/** Every date of the repeat from [from] to [to], inclusive, within its end. */
fun RepeatRule.datesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
    if (to < from) return emptyList()
    val dates = mutableListOf<LocalDate>()
    var index = 0
    while (index <= MAX_STEPS) {
        if (end is RepeatEnd.After && index >= end.times) break
        val date = occurrence(index)
        if (date > to) break
        if (end is RepeatEnd.OnDate && date > end.last) break
        if (date >= from) dates += date
        index++
    }
    return dates
}

/**
 * The dates still to add on [today]: every date after [RepeatRule.lastAdded] up to today, so a
 * phone that was off for three months catches up on all three, and none twice.
 */
fun RepeatRule.due(today: LocalDate): List<LocalDate> {
    if (!active) return emptyList()
    val from = lastAdded?.plus(DatePeriod(days = 1)) ?: start
    // Oldest first and at most this many per run: the rest come on the next run, never all at once.
    return datesBetween(from, today).take(MAX_CATCH_UP)
}

/**
 * Where a phone starts on a repeat it receives (#189): never before yesterday. Dates already gone
 * are the adding phone's to write, and arrive by sync with their fixed ids, so a repeat synced
 * with a start far back cannot make every other phone write years of entries.
 */
fun RepeatRule.firstLastAdded(today: LocalDate): LocalDate? = today.minus(DatePeriod(days = 1)).takeIf { it >= start }

/** Still adding entries on [today]: active, with a date left to add or still to come. One that ran out of dates is done. */
fun RepeatRule.running(today: LocalDate): Boolean = active && (due(today).isNotEmpty() || next(today, count = 1).isNotEmpty())

/** p33's NEXT THREE: the next [count] dates after [today]. */
fun RepeatRule.next(today: LocalDate, count: Int = NEXT_SHOWN): List<LocalDate> {
    if (!active) return emptyList()
    val dates = mutableListOf<LocalDate>()
    var index = 0
    while (dates.size < count) {
        if (end is RepeatEnd.After && index >= end.times) break
        val date = occurrence(index)
        if (end is RepeatEnd.OnDate && date > end.last) break
        if (date > today) dates += date
        index++
        if (index > MAX_STEPS) break
    }
    return dates
}

/** The day before [date], when the reminder fires. */
fun LocalDate.dayBefore(): LocalDate = minus(DatePeriod(days = 1))

/** "The 1st of each month", the On row's words (p33). */
fun RepeatRule.anchorLine(): String {
    val phrases = AppText.copy.phrases
    return when (frequency) {
        RepeatFrequency.Weekly -> phrases.everyWeekday(start.dayOfWeek.label())
        RepeatFrequency.TwoWeeks -> phrases.everyOtherWeekday(start.dayOfWeek.label())
        RepeatFrequency.Monthly -> phrases.monthlyOn(start.day.ordinal())
        RepeatFrequency.Yearly -> phrases.yearlyOn(start.day, start.month.label())
    }
}

/** #189: an added date has a fixed id, so phones that add it at once write one entry. */
fun RepeatRule.entryId(date: LocalDate): String = "repeat:$id:$date"

/** [index] months on, with the day held back to the month's last when the month is shorter. */
private fun LocalDate.monthsLater(index: Int): LocalDate {
    val months = year * MONTHS_PER_YEAR + (month.number - 1) + index
    val target = YearMonth(months / MONTHS_PER_YEAR, months % MONTHS_PER_YEAR + 1)
    return LocalDate(target.year, target.month, minOf(day, target.numberOfDays))
}

fun Int.ordinal(): String = AppText.copy.phrases.ordinal(this)

private fun DayOfWeek.label(): String = AppText.copy.calendar.weekdays[isoDayNumber - 1]

private fun Month.label(): String = AppText.copy.calendar.monthsShort[number - 1]

private const val WEEK = 7
private const val MONTHS_PER_YEAR = 12
private const val NEXT_SHOWN = 3

/** A repeat that ended long ago still stops looking after this many dates. */
private const val MAX_STEPS = 10_000

/** A year of weekly dates: more than any real gap, few enough to write in one run. */
private const val MAX_CATCH_UP = 60
