package com.ptut.expense.detail

import com.ptut.expense.domain.LedgerPeriod
import com.ptut.expense.locale.AppText

val LedgerPeriod.label: String
    get() = when (this) {
        LedgerPeriod.Today -> AppText.copy.common.today
        LedgerPeriod.ThisWeek -> AppText.copy.common.thisWeek
        LedgerPeriod.ThisMonth -> AppText.copy.common.thisMonth
    }.lowercase()

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
