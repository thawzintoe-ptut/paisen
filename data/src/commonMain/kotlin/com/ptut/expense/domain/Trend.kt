package com.ptut.expense.domain

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
