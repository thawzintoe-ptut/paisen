package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang

/**
 * #134: one total into parts that add up to it exactly, in the expense's own minor units and again
 * in satang. p36: "the odd unit is assigned by a deterministic rule"; decided 13 Oct (D7) as You
 * first, then members A–Z by name, among the people with a part.
 */

/** Someone in a split, with what the odd-unit order needs to know about them. */
data class SplitMember(val id: String, val name: String, val isMe: Boolean)

/** D7's order: You first, then by name, then by id so two people with one name still sort the same way twice. */
fun List<SplitMember>.inOddUnitOrder(): List<SplitMember> =
    sortedWith(compareBy<SplitMember>({ !it.isMe }, { it.name.lowercase() }, { it.id }))

/**
 * What the live check on p29 reads. [left] and [assigned] are in the mode's unit: minor units for
 * Exact, basis points for Percent (100.00% is [WHOLE_PERCENT]), and zero for the modes that always
 * add up. A negative [left] means more than the total was given out.
 */
data class SplitCheck(
    /** Member id to their part in minor units. Members with nothing are left out. */
    val parts: Map<String, Long>,
    val left: Long,
    val assigned: Long,
    val target: Long,
    val complete: Boolean,
)

/** 100.00% in basis points: Percent takes two decimals (D11). */
const val WHOLE_PERCENT = 10_000L

fun split(totalMinor: Long, mode: SplitMode, members: List<SplitMember>, weights: Map<String, Long>): SplitCheck {
    val ordered = members.inOddUnitOrder()
    return when (mode) {
        SplitMode.Equally -> {
            val parts = allocate(totalMinor, ordered.associate { it.id to 1L }, ordered)
            SplitCheck(parts, left = 0, assigned = 0, target = 0, complete = ordered.isNotEmpty() && totalMinor > 0)
        }

        SplitMode.Exact -> {
            val parts = ordered.associate { it.id to (weights[it.id] ?: 0L) }.filterValues { it > 0 }
            val assigned = parts.values.sum()
            SplitCheck(parts, left = totalMinor - assigned, assigned = assigned, target = totalMinor, complete = assigned == totalMinor && parts.isNotEmpty())
        }

        SplitMode.Percent -> {
            val points = ordered.associate { it.id to (weights[it.id] ?: 0L) }.filterValues { it > 0 }
            val assigned = points.values.sum()
            val complete = assigned == WHOLE_PERCENT && totalMinor > 0
            val parts = if (complete) allocate(totalMinor, points, ordered) else points.mapValues { (_, bp) -> totalMinor * bp / WHOLE_PERCENT }
            SplitCheck(parts, left = WHOLE_PERCENT - assigned, assigned = assigned, target = WHOLE_PERCENT, complete = complete)
        }

        SplitMode.Shares -> {
            val counts = ordered.associate { it.id to (weights[it.id] ?: 0L) }.filterValues { it > 0 }
            val parts = allocate(totalMinor, counts, ordered)
            SplitCheck(parts, left = 0, assigned = counts.values.sum(), target = 0, complete = counts.isNotEmpty() && totalMinor > 0)
        }
    }
}

/**
 * [total] in proportion to [weights], each part rounded down, then the units rounding dropped
 * handed out one each in D7's order. The parts always add up to [total].
 */
internal fun allocate(total: Long, weights: Map<String, Long>, ordered: List<SplitMember>): Map<String, Long> {
    val sum = weights.values.sum()
    if (sum <= 0) return emptyMap()
    val takers = ordered.filter { (weights[it.id] ?: 0L) > 0 }
    // Through 128 bits: a big total times a big Exact part passes a Long's range.
    val parts = takers.associate { it.id to share(total, weights.getValue(it.id), sum) }.toMutableMap()
    var odd = total - parts.values.sum()
    var index = 0
    while (odd > 0 && takers.isNotEmpty()) {
        val id = takers[index % takers.size].id
        parts[id] = parts.getValue(id) + 1
        odd--
        index++
    }
    return parts
}

/** [total] × [weight] ÷ [sum], rounded toward zero, for a total of either sign. */
private fun share(total: Long, weight: Long, sum: Long): Long {
    val magnitude = mulDivRem(if (total < 0) -total else total, weight, sum).first
    return if (total < 0) -magnitude else magnitude
}

/**
 * The finished split as stored: each part in its currency and in satang. The satang parts are
 * handed out from the converted total the same way, so they add up to it to the satang (#134).
 */
fun shares(check: SplitCheck, totalSatang: Satang, members: List<SplitMember>, weights: Map<String, Long>, mode: SplitMode): List<Share> {
    val ordered = members.inOddUnitOrder()
    val satang = allocate(totalSatang.toMinorUnits(), check.parts, ordered)
    return ordered.filter { it.id in check.parts }.map { member ->
        Share(
            memberId = member.id,
            weight = when (mode) {
                SplitMode.Equally -> 1L
                SplitMode.Exact -> check.parts.getValue(member.id)
                else -> weights[member.id] ?: 0L
            },
            amountMinor = check.parts.getValue(member.id),
            satang = Satang.ofMinorUnits(satang[member.id] ?: 0L),
        )
    }
}

/** p29's GIVE TO <name>: whatever is left goes on [memberId], the field edited last. */
fun Map<String, Long>.giveLeftTo(memberId: String, left: Long): Map<String, Long> =
    toMutableMap().apply { this[memberId] = ((this[memberId] ?: 0L) + left).coerceAtLeast(0L) }

/**
 * O1: when the total changes under an Exact split, the difference goes on the payer's part, so
 * nobody else's share moves. A payer pushed below zero is held at zero and the check shows the rest.
 */
fun Map<String, Long>.rebalancedTo(newTotalMinor: Long, payerId: String): Map<String, Long> =
    giveLeftTo(payerId, newTotalMinor - values.sum())
