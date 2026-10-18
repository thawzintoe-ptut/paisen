package com.ptut.expense.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** One bar of a period's chart: the days it covers and what was spent on them. */
data class BucketSpend(val start: LocalDate, val endInclusive: LocalDate, val spent: Satang) {
    operator fun contains(date: LocalDate): Boolean = date >= start && date <= endInclusive
}

/**
 * FR-17 for every period (#164): [range] cut into [size] buckets, empty ones included, so the chart
 * keeps its width whatever happened. The last bucket stops where the range does.
 *
 * Income is excluded, as in [spendPerDay], so the bars add up to the expense total above them.
 */
fun List<Transaction>.spendPerBucket(
    range: DateRange,
    size: BucketSize,
    zone: TimeZone = BangkokTime,
): List<BucketSpend> {
    val spentOn = filter { it.direction == Direction.Expense }
        .groupBy { it.occurredAtEpochMillis.dateIn(zone) }
        .mapValues { (_, rows) -> rows.map { it.amount }.sum() }

    return generateSequence(range.start) { it.nextBucketStart(size) }
        .takeWhile { it <= range.endInclusive }
        .map { start ->
            val end = minOf(start.nextBucketStart(size).minus(1, DateTimeUnit.DAY), range.endInclusive)
            BucketSpend(start, end, spentOn.filterKeys { it >= start && it <= end }.values.sum())
        }
        .toList()
}

/**
 * The dashed line: mean spend per bucket, over the buckets that have begun by [today]. A bucket
 * still ahead has not had its chance, and counting it as zero would pull the line down.
 * Null when no bucket has begun.
 */
fun List<BucketSpend>.averageBy(today: LocalDate): Satang? {
    val begun = filter { it.start <= today }
    if (begun.isEmpty()) return null
    return Satang.ofMinorUnits(begun.map { it.spent }.sum().toMinorUnits() / begun.size)
}

private fun LocalDate.nextBucketStart(size: BucketSize): LocalDate = when (size) {
    BucketSize.Day -> plus(1, DateTimeUnit.DAY)

    BucketSize.Week -> plus(1, DateTimeUnit.WEEK)

    // From the first of this month, so a range that starts mid-month still steps month by month.
    BucketSize.Month -> LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH)
}
