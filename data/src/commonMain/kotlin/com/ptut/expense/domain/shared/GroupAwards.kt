package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.mondayOfWeekOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil

/** An award needs at least this many members who qualify, or it is left out (D11). */
private const val MIN_QUALIFYING = 2

/** D11: praise only. Nothing here names who owes most or paid least. */
enum class AwardKind { FastestToSettle, KeptTheBooks, SteadyPayer }

/** [count] is the number behind the title: days, entries or weeks. */
data class Award(val kind: AwardKind, val memberId: String, val count: Int)

/**
 * #96: the month's awards for [groupId], [month] ISO `yyyy-MM`. Members who turned group stamps off
 * (D5) are never named. Ties go to the lower member id, and days and weeks are cut in UTC rather than
 * each phone's home zone, so every phone names the same person.
 */
fun SharedSnapshot.groupAwards(groupId: String, month: String, zone: TimeZone = TimeZone.UTC): List<Award> {
    val group = group(groupId) ?: return emptyList()
    val members = group.activeMembers.filterNot { it.playOff }.map { it.id }.toSet()
    val inMonth: (Long) -> Boolean = { millis -> millis.dateIn(zone).toString().startsWith(month) }
    val payments = paymentsIn(groupId).filter { inMonth(it.occurredAtEpochMillis) && it.fromMemberId in members }
    val expenses = currentExpenses(groupId)

    val fastest = payments.groupBy { it.fromMemberId }.mapValues { (member, paid) ->
        paid.mapNotNull { payment -> waitDays(member, payment, expenses, zone) }.median()
    }.filterValues { it != null }.mapValues { it.value ?: 0 }
    val books = expenseVersions.filter { it.groupId == groupId && it.version == 1 && inMonth(it.createdAtEpochMillis) }.mapNotNull { it.authorMemberId } +
        paymentsIn(groupId).filter { inMonth(it.createdAtEpochMillis) }.mapNotNull { it.authorMemberId }
    val kept = books.filter { it in members }.groupingBy { it }.eachCount()
    val steady = payments.groupBy { it.fromMemberId }.mapValues { (_, paid) -> paid.map { mondayOfWeekOf(it.occurredAtEpochMillis.dateIn(zone)) }.toSet().size }

    return listOfNotNull(
        fastest.pick(AwardKind.FastestToSettle, lowest = true),
        kept.pick(AwardKind.KeptTheBooks, lowest = false),
        steady.pick(AwardKind.SteadyPayer, lowest = false),
    )
}

private fun Map<String, Int>.pick(kind: AwardKind, lowest: Boolean): Award? {
    if (size < MIN_QUALIFYING) return null
    val order = compareBy<Map.Entry<String, Int>> { if (lowest) it.value else -it.value }.thenBy { it.key }
    return entries.minWithOrNull(order)?.let { Award(kind, it.key, it.value) }
}

/** Days from the latest expense that gave [member] a share, before [payment], to the payment. */
private fun waitDays(member: String, payment: SharedPayment, expenses: List<SharedExpense>, zone: TimeZone): Int? {
    val shared = expenses
        .filter { it.payerMemberId != member && it.occurredAtEpochMillis <= payment.occurredAtEpochMillis && it.shares.any { share -> share.memberId == member && share.satang > Satang.Zero } }
        .maxOfOrNull { it.occurredAtEpochMillis } ?: return null
    return shared.dateIn(zone).daysUntil(payment.occurredAtEpochMillis.dateIn(zone))
}

private fun List<Int>.median(): Int? = if (isEmpty()) null else sorted()[(size - 1) / 2]
