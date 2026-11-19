package com.ptut.expense.home

import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.TrendDirection
import com.ptut.expense.domain.UsualDay
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.longName
import com.ptut.expense.domain.trendBetween
import com.ptut.expense.locale.AppText

/**
 * "฿180 less than a usual Friday", or "…your usual Friday" on the receipt (p05, p09). It says
 * less, more or the same, and never a word that judges.
 */
fun UsualDay.differencePhrase(whose: String = "a"): String {
    val phrases = AppText.copy.phrases
    val weekday = day.dayOfWeek.longName()
    return when {
        difference < Satang.Zero -> phrases.lessThanUsual((-difference).formatCompact(), whose, weekday)
        difference > Satang.Zero -> phrases.moreThanUsual(difference.formatCompact(), whose, weekday)
        else -> phrases.sameAsUsual(whose, weekday)
    }
}

/** "8% under a usual Thursday" (p07). A usual day of ฿0 has no percent, so it says the amount instead. */
fun UsualDay.percentPhrase(): String {
    val trend = trendBetween(usual, spent)
    val percent = trend.percent ?: return differencePhrase()
    val phrases = AppText.copy.phrases
    val weekday = day.dayOfWeek.longName()
    return when (trend.direction) {
        TrendDirection.Down -> phrases.percentUnderUsual(-percent, weekday)
        TrendDirection.Up -> phrases.percentOverUsual(percent, weekday)
        TrendDirection.Flat -> phrases.sameAsUsual("a", weekday)
    }
}
