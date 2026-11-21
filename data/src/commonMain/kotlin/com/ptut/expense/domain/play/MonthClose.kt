package com.ptut.expense.domain.play

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.total
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth

/** A cost this many times the month's median row is called large. */
private const val LARGE_TIMES = 3L

/** p20's second step shows at most this many. */
private const val LARGE_SHOWN = 3

/**
 * #98: what p20's three steps look at before a month is sealed. [duplicates] are pairs of row ids with
 * the same shop, amount and day; [risenRepeats] the rows of a shop paid every month that came to more
 * this time; [large] the biggest rows well above the month's usual; [versusLastMonth] this month less the one before.
 */
data class MonthClose(
    val duplicates: List<Pair<String, String>>,
    val risenRepeats: List<String>,
    val large: List<String>,
    val versusLastMonth: Satang?,
) {
    /** Risen and large merged by row id, so a row that is both shows once (risen order first). */
    val bigCosts: List<BigCost>
        get() = (risenRepeats + large).distinct().map { BigCost(it, risen = it in risenRepeats, large = it in large) }
}

/** One row in p20's second step and why it is there. */
data class BigCost(val id: String, val risen: Boolean, val large: Boolean)

/** Queued slips dated inside [month]; a slip from another month neither blocks the seal nor counts. */
fun List<QueuedSlip>.waitingIn(month: String, zone: TimeZone = HomeTime): Int {
    val yearMonth = runCatching { YearMonth.parse(month) }.getOrNull() ?: return 0
    return count { it.occurredAtEpochMillis.dateIn(zone).let { day -> day.year == yearMonth.year && day.month == yearMonth.month } }
}

fun List<Transaction>.monthClose(month: String, zone: TimeZone = HomeTime): MonthClose {
    val yearMonth = runCatching { YearMonth.parse(month) }.getOrNull() ?: return MonthClose(emptyList(), emptyList(), emptyList(), null)
    val expenses = filter { it.direction == Direction.Expense }
    val rows = expenses.inMonth(yearMonth, zone).sortedBy { it.id }
    val last = expenses.inMonth(yearMonth.minusMonths(1), zone)
    val duplicates = rows.groupBy { Triple(it.counterparty?.shopKey(), it.amount, it.occurredAtEpochMillis.dateIn(zone)) }
        .filterKeys { it.first != null }
        .values.filter { it.size > 1 }
        .flatMap { rows -> rows.drop(1).map { rows[0].id to it.id } }
    val lastByShop = last.mapNotNull { row -> row.counterparty?.shopKey()?.let { it to row.amount } }.groupBy({ it.first }, { it.second })
    val risen = rows.filter { row ->
        val shop = row.counterparty?.shopKey() ?: return@filter false
        val before = lastByShop[shop]?.maxOrNull() ?: return@filter false
        row.amount > before
    }.map { it.id }
    val median = rows.map { it.amount }.sorted().let { if (it.isEmpty()) Satang.Zero else it[it.size / 2] }
    val large = rows.filter { it.amount.toMinorUnits() >= median.toMinorUnits() * LARGE_TIMES && it.amount > Satang.Zero }
        .sortedByDescending { it.amount }
        .take(LARGE_SHOWN)
        .map { it.id }
    return MonthClose(duplicates, risen, large, if (last.isEmpty()) null else rows.total() - last.total())
}
