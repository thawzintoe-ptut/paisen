package com.ptut.expense.domain

import com.ptut.expense.locale.AppText

/**
 * US-8. A watcher that dies quietly while spending continues is the failure that makes every
 * report lie, so silence is only the healthy state for the first day.
 */
enum class ImportFreshness { Fresh, Stale, Broken }

private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000
private const val THREE_DAYS_MILLIS = 3 * ONE_DAY_MILLIS

fun importFreshness(sinceLastImportMillis: Long): ImportFreshness = when {
    sinceLastImportMillis < ONE_DAY_MILLIS -> ImportFreshness.Fresh
    sinceLastImportMillis <= THREE_DAYS_MILLIS -> ImportFreshness.Stale
    else -> ImportFreshness.Broken
}

private const val ONE_MINUTE_MILLIS = 60L * 1000
private const val ONE_HOUR_MILLIS = 60 * ONE_MINUTE_MILLIS

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
