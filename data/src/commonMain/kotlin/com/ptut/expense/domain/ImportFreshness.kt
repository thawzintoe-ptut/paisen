package com.ptut.expense.domain

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
