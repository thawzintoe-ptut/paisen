package com.ptut.expense.shared

import com.ptut.expense.domain.shared.RepeatFrequency
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.locale.AppText
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

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

fun Int.ordinal(): String = AppText.copy.phrases.ordinal(this)

private fun DayOfWeek.label(): String = AppText.copy.calendar.weekdays[isoDayNumber - 1]

private fun Month.label(): String = AppText.copy.calendar.monthsShort[number - 1]
