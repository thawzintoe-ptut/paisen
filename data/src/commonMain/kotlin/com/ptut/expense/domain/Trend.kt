package com.ptut.expense.domain

import com.ptut.expense.locale.AppText

enum class TrendDirection { Up, Down, Flat }

/**
 * [percent] is null when the previous period was zero: there is no baseline, so any
 * percentage would be invented. The UI says "new" rather than showing a made-up number.
 */
data class Trend(
    val percent: Int?,
    val direction: TrendDirection,
)

fun trendBetween(previous: Satang, current: Satang): Trend {
    val direction = when {
        current > previous -> TrendDirection.Up
        current < previous -> TrendDirection.Down
        else -> TrendDirection.Flat
    }
    if (previous == Satang.Zero) {
        return Trend(percent = if (direction == TrendDirection.Flat) 0 else null, direction = direction)
    }
    // The Double is safe: it only ever becomes a truncated percent, and no money is derived
    // from it. Do not "fix" this into Satang arithmetic.
    val change = (current - previous).raw * 100.0 / previous.raw
    return Trend(percent = change.toInt(), direction = direction)
}

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
