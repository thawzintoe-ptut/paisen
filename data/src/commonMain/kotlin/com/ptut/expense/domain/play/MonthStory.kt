package com.ptut.expense.domain.play

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.inWeek
import com.ptut.expense.domain.mondayOfWeek
import com.ptut.expense.domain.total
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** "A usual month" is the mean of up to three before it. */
private const val USUAL_MONTHS = 3

/** #90: one card of the month's story, each with the number it is built from (R8). */
sealed interface StoryCard {
    /** The month's spending, and how it differs from a usual month, or null with none before it. */
    data class Total(val spent: Satang, val versusUsual: Satang?) : StoryCard

    /** The category that moved most against a usual month. */
    data class BiggestShift(val categoryId: String, val difference: Satang) : StoryCard

    /** The shop first seen this month that took most. */
    data class NewShop(val name: String, val amount: Satang) : StoryCard

    /** The week of the month that cost least. */
    data class QuietestWeek(val monday: LocalDate, val spent: Satang) : StoryCard

    data class NoSpendDays(val count: Int) : StoryCard
}

/**
 * #90: up to five cards for [month] (ISO `yyyy-MM`), each left out when the month has nothing to say
 * for it. The shift is whichever category was most unusual; ties go to the lower category id.
 */
fun List<Transaction>.monthStory(month: String, zone: TimeZone = HomeTime, today: LocalDate? = null): List<StoryCard> {
    val yearMonth = runCatching { YearMonth.parse(month) }.getOrNull() ?: return emptyList()
    val expenses = filter { it.direction == Direction.Expense }
    val inMonth = expenses.inMonth(yearMonth, zone)
    if (inMonth.isEmpty()) return emptyList()
    val earlier = (1..USUAL_MONTHS).map { yearMonth.minusMonths(it) }.map { expenses.inMonth(it, zone) }.filter { it.isNotEmpty() }
    val usual = earlier.takeIf { it.isNotEmpty() }?.let { months -> Satang.ofMinorUnits(months.sumOf { it.total().toMinorUnits() } / months.size) }
    val spent = inMonth.total()
    return listOfNotNull(
        StoryCard.Total(spent, usual?.let { spent - it }),
        biggestShift(inMonth, earlier),
        newShop(inMonth, expenses.filter { it.occurredAtEpochMillis.dateIn(zone) < yearMonth.firstDay }),
        quietestWeek(expenses, yearMonth, zone),
        // Days still to come in this month are not no-spend days yet.
        noSpendDays(month, today?.let { minOf(it, yearMonth.lastDay) } ?: yearMonth.lastDay, zone).size.takeIf { it > 0 }?.let { StoryCard.NoSpendDays(it) },
    )
}

private fun biggestShift(month: List<Transaction>, earlier: List<List<Transaction>>): StoryCard.BiggestShift? {
    if (earlier.isEmpty()) return null
    val ids = (month + earlier.flatten()).mapNotNull { it.categoryId }.distinct().sorted()
    return ids.map { id ->
        val now = month.filter { it.categoryId == id }.total().toMinorUnits()
        val usual = earlier.sumOf { rows -> rows.filter { it.categoryId == id }.total().toMinorUnits() } / earlier.size
        StoryCard.BiggestShift(id, Satang.ofMinorUnits(now - usual))
    }.filter { it.difference != Satang.Zero }
        .maxWithOrNull(compareBy<StoryCard.BiggestShift> { it.difference.abs() }.thenByDescending { it.categoryId })
}

private fun newShop(month: List<Transaction>, before: List<Transaction>): StoryCard.NewShop? {
    val known = before.mapNotNull { it.counterparty?.shopKey() }.toSet()
    return month.mapNotNull { row -> row.counterparty?.trim()?.takeIf { it.isNotEmpty() && it.shopKey() !in known }?.let { it to row.amount } }
        .groupBy({ it.first }, { it.second })
        .map { (name, amounts) -> StoryCard.NewShop(name, Satang.ofMinorUnits(amounts.sumOf { it.toMinorUnits() })) }
        .maxWithOrNull(compareBy<StoryCard.NewShop> { it.amount }.thenByDescending { it.name })
}

/** Only weeks that start inside the month, so a quiet week is the month's own. */
private fun quietestWeek(expenses: List<Transaction>, month: YearMonth, zone: TimeZone): StoryCard.QuietestWeek? {
    var monday = month.firstDay.mondayOfWeek().let { if (it < month.firstDay) it.plus(1, DateTimeUnit.WEEK) else it }
    val weeks = mutableListOf<StoryCard.QuietestWeek>()
    while (monday.plus(DAYS_LAST, DateTimeUnit.DAY) <= month.lastDay) {
        weeks += StoryCard.QuietestWeek(monday, expenses.inWeek(monday, zone).total())
        monday = monday.plus(1, DateTimeUnit.WEEK)
    }
    return weeks.minByOrNull { it.spent }
}

internal fun List<Transaction>.inMonth(month: YearMonth, zone: TimeZone): List<Transaction> =
    filter { it.occurredAtEpochMillis.dateIn(zone).let { day -> day >= month.firstDay && day <= month.lastDay } }

internal fun YearMonth.minusMonths(count: Int): YearMonth = firstDay.minus(count, DateTimeUnit.MONTH).let { YearMonth(it.year, it.month) }

private const val DAYS_LAST = 6

/** Shops compare by name alone: "7-Eleven" and "7-eleven " are one shop. */
internal fun String.shopKey(): String = trim().lowercase()
