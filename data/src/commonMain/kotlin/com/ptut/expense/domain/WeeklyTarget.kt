package com.ptut.expense.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/** Weeks behind the suggestion, as for "a usual week" (#20). */
private const val SUGGEST_WEEKS = 4

/** Fewer earlier weeks than this, and nothing is suggested. */
private const val SUGGEST_MIN_WEEKS = 2

/** A suggestion is rounded down to 100 whole units (฿100, K100), so it reads as a number someone chose. */
private fun suggestStep(): Long = 100 * minorPerWhole()

/**
 * #21: spend less than [amount] a week on one category. One target at a time, and never a budget
 * per category: the 2026-10-14 "no budget" decision gives way for this one light target only.
 */
data class WeeklyTarget(val categoryId: String, val amount: Satang)

/** One week's spending in the target's category beside the target. */
data class TargetProgress(val spent: Satang, val target: Satang) {

    /** Positive past the target, negative while under it. */
    val over: Satang get() = spent - target

    /** For the bar: full at the target and never further, so going past draws no louder. */
    val fraction: Float get() = if (target <= Satang.Zero) 1f else spent.ratioTo(target).coerceIn(0f, 1f)
}

/** This week so far, Monday to [today]. */
fun List<Transaction>.thisWeek(target: WeeklyTarget, today: LocalDate, zone: TimeZone = HomeTime): TargetProgress =
    progressFrom(target, today.mondayOfWeek(), zone)

/** The whole of last week, for Monday's card. */
fun List<Transaction>.lastWeek(target: WeeklyTarget, today: LocalDate, zone: TimeZone = HomeTime): TargetProgress =
    progressFrom(target, today.mondayOfWeek().minus(1, DateTimeUnit.WEEK), zone)

/**
 * A starting target: the category's usual week, the mean of up to four weeks before this one, from
 * the ledger's first week on, rounded down to ฿100. Null with under two weeks or nothing spent.
 */
fun List<Transaction>.suggestedTarget(categoryId: String, today: LocalDate, zone: TimeZone = HomeTime): Satang? {
    val spending = filter { it.direction == Direction.Expense && it.categoryId == categoryId }
    if (isEmpty()) return null
    val firstMonday = minOf { it.occurredAtEpochMillis }.dateIn(zone).mondayOfWeek()
    val weeks = (1..SUGGEST_WEEKS)
        .map { today.mondayOfWeek().minus(it, DateTimeUnit.WEEK) }
        .filter { it >= firstMonday }
    if (weeks.size < SUGGEST_MIN_WEEKS) return null
    val usual = weeks.map { spending.inWeek(it, zone).total() }.mean()
    val step = suggestStep()
    return Satang(usual.raw / step * step).takeIf { it > Satang.Zero }
}

private fun List<Transaction>.progressFrom(target: WeeklyTarget, monday: LocalDate, zone: TimeZone) = TargetProgress(
    spent = filter { it.direction == Direction.Expense && it.categoryId == target.categoryId }.inWeek(monday, zone).total(),
    target = target.amount,
)
