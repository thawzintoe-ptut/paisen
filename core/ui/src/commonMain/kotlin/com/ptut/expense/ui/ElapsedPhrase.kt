package com.ptut.expense.ui

import com.ptut.expense.locale.AppText

private const val ONE_MINUTE_MILLIS = 60L * 1000
private const val ONE_HOUR_MILLIS = 60 * ONE_MINUTE_MILLIS
private const val ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS

/**
 * A bare span — "3 days" — so a caller can put it in either sentence the banner needs:
 * "imported 3 days ago" and "no slips seen for 3 days". Rounds down to the largest whole
 * unit; the first minute is named rather than counted, because "0 min" reads as a dead clock.
 */
fun elapsedPhrase(millis: Long): String {
    val phrases = AppText.copy.phrases
    return when {
        millis < ONE_MINUTE_MILLIS -> phrases.underAMinute
        millis < ONE_HOUR_MILLIS -> phrases.minutes(millis / ONE_MINUTE_MILLIS)
        millis < ONE_DAY_MILLIS -> phrases.hours(millis / ONE_HOUR_MILLIS)
        else -> phrases.days(millis / ONE_DAY_MILLIS)
    }
}
