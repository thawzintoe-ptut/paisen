package com.ptut.expense.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/** How many earlier months make "your usual by this day": three, so one odd month does not set it. */
private const val USUAL_MONTHS = 3

/**
 * This month's spending so far beside the same stretch of earlier months. Against the reader's own
 * history, never a budget. [usual] is null until the ledger holds one whole earlier month.
 */
data class MonthPace(val spent: Satang, val usual: Satang?) {
    val difference: Satang? get() = usual?.let { spent - it }
}

/**
 * The month to [today], and the median of the last [USUAL_MONTHS] months each cut at the same date.
 * A month counts only when the ledger began on or before its first day: a month the reader joined
 * halfway through would read as a quiet one.
 */
fun List<Transaction>.monthPace(today: LocalDate, zone: TimeZone = HomeTime): MonthPace {
    val spent = inside(ReportPeriod.Month.rangeContaining(today), zone).summarise().expense
    val first = minOfOrNull { it.occurredAtEpochMillis }?.dateIn(zone) ?: return MonthPace(spent, usual = null)
    val earlier = (1..USUAL_MONTHS)
        .map { ReportPeriod.Month.rangeContaining(today.minus(it, DateTimeUnit.MONTH)) }
        .filter { first <= it.start }
        .map { inside(it, zone).summarise().expense }
    return MonthPace(spent, usual = earlier.takeIf { it.isNotEmpty() }?.median())
}
