package com.ptut.expense.domain.play

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.minorPerWhole
import com.ptut.expense.domain.usualDay
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** A category's day stands out when it is this far from usual, in whole money ... */
private const val COACH_MIN_WHOLE = 100L

/** ... and this many percent of the usual day. */
private const val COACH_MIN_PERCENT = 30L

/**
 * #89: the one category whose spending today is furthest from a usual day of this weekday, with the
 * rows behind the number. [difference] is above zero when today came to more.
 */
data class CoachLine(val categoryId: String, val difference: Satang, val weekday: DayOfWeek, val rowIds: List<String>)

/**
 * #89 on Today's "usual" maths (#119, D9): each category's spending today beside the median of the same
 * weekday over eight weeks. Null when nothing stands out by both [COACH_MIN_WHOLE] and
 * [COACH_MIN_PERCENT], so most days say nothing.
 */
fun List<Transaction>.coachLine(today: LocalDate, zone: TimeZone = HomeTime): CoachLine? {
    val expenses = filter { it.direction == Direction.Expense && it.categoryId != null }
    val minWhole = Satang.ofMinorUnits(COACH_MIN_WHOLE * minorPerWhole())
    return expenses.mapNotNull { it.categoryId }.distinct().sorted().mapNotNull { id ->
        val rows = expenses.filter { it.categoryId == id }
        val usual = rows.usualDay(today, zone) ?: return@mapNotNull null
        val difference = usual.difference
        val size = difference.abs()
        val standsOut = size >= minWhole && size.toMinorUnits() * PERCENT >= usual.usual.toMinorUnits() * COACH_MIN_PERCENT
        if (!standsOut) return@mapNotNull null
        CoachLine(id, difference, today.dayOfWeek, rows.filter { it.occurredAtEpochMillis.dateIn(zone) == today }.map { it.id })
    }.maxWithOrNull(compareBy<CoachLine> { it.difference.abs() }.thenByDescending { it.categoryId })
}
