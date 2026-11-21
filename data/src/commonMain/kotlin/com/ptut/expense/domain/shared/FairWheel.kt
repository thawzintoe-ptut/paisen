package com.ptut.expense.domain.shared

import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.dateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/** "Lately" on the wheel: rounds paid in the last 30 days (#94). */
private const val WHEEL_DAYS = 30

/** "A usual round": the median expense of the last 60 days. */
private const val USUAL_ROUND_DAYS = 60

/** One member's slice. [weight] is relative to the others'; [roundsPaid] is what the PaiSeN line explains. */
data class WheelSlice(val memberId: String, val weight: Int, val roundsPaid: Int)

/**
 * #94: every active member's slice, ordered by member id so the same group always draws the same wheel.
 * A fair wheel gives whoever paid fewer rounds lately the bigger slice; an equal one gives all the same.
 */
fun SharedSnapshot.wheelSlices(groupId: String, today: LocalDate, fair: Boolean, zone: TimeZone = HomeTime): List<WheelSlice> {
    val members = group(groupId)?.activeMembers?.sortedBy { it.id }.orEmpty()
    val since = today.minus(WHEEL_DAYS - 1, DateTimeUnit.DAY)
    val rounds = currentExpenses(groupId)
        .filter { it.occurredAtEpochMillis.dateIn(zone) in since..today }
        .groupingBy { it.payerMemberId }
        .eachCount()
    val most = members.maxOfOrNull { rounds[it.id] ?: 0 } ?: 0
    return members.map { member ->
        val paid = rounds[member.id] ?: 0
        WheelSlice(member.id, weight = if (fair) most - paid + 1 else 1, roundsPaid = paid)
    }
}

/** #94: the median expense in the last 60 days, or null with none. */
fun SharedSnapshot.usualRound(groupId: String, today: LocalDate, zone: TimeZone = HomeTime): Satang? {
    val since = today.minus(USUAL_ROUND_DAYS - 1, DateTimeUnit.DAY)
    val amounts = currentExpenses(groupId).filter { it.occurredAtEpochMillis.dateIn(zone) in since..today }.map { it.satang }.sorted()
    if (amounts.isEmpty()) return null
    val middle = amounts.size / 2
    return if (amounts.size % 2 == 1) amounts[middle] else Satang.ofMinorUnits((amounts[middle - 1].toMinorUnits() + amounts[middle].toMinorUnits()) / 2)
}

/** The member under the pointer when the wheel stops [spin] of the way round, 0 to 1. Null on an empty wheel. */
fun List<WheelSlice>.landing(spin: Float): String? {
    val total = sumOf { it.weight }
    if (total <= 0) return null
    val point = spin.coerceIn(0f, 1f) * total
    var reached = 0
    forEach { slice ->
        reached += slice.weight
        if (point < reached) return slice.memberId
    }
    return last().memberId
}
