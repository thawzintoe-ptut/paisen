package com.ptut.expense.domain

import com.ptut.expense.locale.AppText
import kotlinx.datetime.LocalDate

/**
 * The three totals a delete can move. FR-21's dialog names them, and naming the wrong ones is
 * worse than naming none: a reader told that this week changes when it does not will not trust
 * the next thing the screen says about their money.
 */
enum class LedgerPeriod {
    Today,
    ThisWeek,
    ThisMonth,
    ;

    val label: String
        get() = when (this) {
            Today -> AppText.copy.common.today
            ThisWeek -> AppText.copy.common.thisWeek
            ThisMonth -> AppText.copy.common.thisMonth
        }.lowercase()
}

/**
 * Which of the three actually move, for a row on [occurredAt] deleted on [today].
 *
 * The ranges come from [ReportPeriod] rather than a second set of date rules, so this and the
 * Reports screen cannot disagree about where a week starts.
 */
fun periodsMovedByDeleting(occurredAt: LocalDate, today: LocalDate): List<LedgerPeriod> =
    LedgerPeriod.entries.filter { period ->
        when (period) {
            LedgerPeriod.Today -> occurredAt == today
            LedgerPeriod.ThisWeek -> occurredAt in ReportPeriod.Week.rangeContaining(today)
            LedgerPeriod.ThisMonth -> occurredAt in ReportPeriod.Month.rangeContaining(today)
        }
    }

/**
 * "today", "this month", "today, this week and this month" — a list the dialog drops into a
 * sentence. Empty when nothing moves, so the caller writes that case as its own sentence rather
 * than printing a dangling "removes it from ".
 */
fun List<LedgerPeriod>.phrase(): String {
    val phrases = AppText.copy.phrases
    return when (size) {
        0 -> ""
        1 -> single().label
        else -> dropLast(1).joinToString(phrases.listComma) { it.label } + phrases.listAnd + last().label
    }
}
