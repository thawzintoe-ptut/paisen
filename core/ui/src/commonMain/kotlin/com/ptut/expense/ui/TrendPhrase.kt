package com.ptut.expense.ui

import com.ptut.expense.domain.Trend
import com.ptut.expense.domain.TrendDirection
import com.ptut.expense.locale.AppText

/**
 * "12% less than last week", "4% more than last week", "the same as last week". Null with no
 * baseline. One wording for Reports and Activity's weekly wrap, so the two never disagree (#127).
 */
fun Trend.phrase(baseline: String): String? {
    val percent = percent ?: return null
    val phrases = AppText.copy.phrases
    return when (direction) {
        TrendDirection.Flat -> phrases.sameAsBaseline(baseline)

        TrendDirection.Up -> phrases.percentMoreThan(percent, baseline)

        // trendBetween signs the percent, and "−20% less" would double the negative.
        TrendDirection.Down -> phrases.percentLessThan(-percent, baseline)
    }
}
