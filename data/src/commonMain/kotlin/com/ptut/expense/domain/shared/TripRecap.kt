package com.ptut.expense.domain.shared

import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.minorPerWhole
import com.ptut.expense.domain.sum
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** The next-trip estimate rounds to this many of the group's money. */
private const val ESTIMATE_STEP_WHOLE = 100L

/** #97: a trip's total, its days and people, the headline per person per day, and each day's spend. */
data class TripRecap(
    val total: Satang,
    val days: Int,
    val people: Int,
    val perPersonPerDay: Satang,
    val byDay: List<Pair<LocalDate, Satang>>,
)

/**
 * #97: from the first expense to the day the trip was marked ended (or the last expense), every day
 * counted, a quiet one as nothing. Null on a trip with no expenses.
 */
fun SharedSnapshot.tripRecap(groupId: String, zone: TimeZone = HomeTime): TripRecap? {
    val group = group(groupId) ?: return null
    val expenses = currentExpenses(groupId)
    if (expenses.isEmpty()) return null
    val byDate = expenses.groupBy { it.occurredAtEpochMillis.dateIn(zone) }.mapValues { (_, rows) -> rows.map { it.satang }.sum() }
    val first = byDate.keys.min()
    val last = maxOf(group.endedOn ?: byDate.keys.max(), byDate.keys.max())
    val days = first.daysUntil(last) + 1
    // Whoever paid or owed a share: a name removed after the trip still counts, one that joined later does not.
    val people = expenses.flatMap { expense -> expense.shares.filter { it.satang > Satang.Zero }.map { it.memberId } + expense.payerMemberId }.toSet().size.coerceAtLeast(1)
    val total = byDate.values.sum()
    return TripRecap(
        total = total,
        days = days,
        people = people,
        perPersonPerDay = Satang.ofMinorUnits(total.toMinorUnits() / (days.toLong() * people)),
        byDay = (0 until days).map { offset -> first.plus(offset, DateTimeUnit.DAY).let { day -> day to (byDate[day] ?: Satang.Zero) } },
    )
}

/** What a trip like this costs one person, and what to put aside each week for [weeks] weeks to have it. */
data class NextTrip(val estimate: Satang, val perWeek: Satang, val weeks: Int)

/** [money] is the group's own: a kyat or yen group rounds in its own units, not the home's. */
fun TripRecap.nextTrip(money: HomeMoney, weeks: Int = NEXT_TRIP_WEEKS): NextTrip {
    val whole = minorPerWhole(money)
    val step = ESTIMATE_STEP_WHOLE * whole
    val share = total.toMinorUnits() / people
    val estimate = (share + step - 1) / step * step
    // Up to a whole unit, so the weeks together reach the estimate.
    val perWeek = (estimate + weeks * whole - 1) / (weeks * whole) * whole
    return NextTrip(Satang.ofMinorUnits(estimate), Satang.ofMinorUnits(perWeek), weeks)
}

/** Twelve weeks is about a season: long enough that a weekly amount stays small. */
const val NEXT_TRIP_WEEKS = 12
