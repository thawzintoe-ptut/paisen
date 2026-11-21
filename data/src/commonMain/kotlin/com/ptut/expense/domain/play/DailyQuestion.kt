package com.ptut.expense.domain.play

import com.ptut.expense.domain.DAYS_IN_WEEK
import com.ptut.expense.domain.DateRange
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.inside
import com.ptut.expense.domain.sum
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/** A pair asked inside this many days is not asked again (#87). */
const val QUESTION_REPEAT_DAYS = 30

/** Two totals are close enough to ask about when they are within this percent of the larger. */
private const val CLOSE_PERCENT = 25L

private const val CATEGORY = "cat:"
private const val SHOP = "shop:"

/** Between the two sides of a key: a character no shop name or category id holds, so a name with "|" still splits right. */
private const val KEY_SEPARATOR = "\u001F"

/** One side of the day's question: a category or a shop, with what it came to last week. */
sealed interface QuestionSide {
    val amount: Satang
    val count: Int

    data class Category(val categoryId: String, override val amount: Satang, override val count: Int) : QuestionSide

    data class Shop(val name: String, override val amount: Satang, override val count: Int) : QuestionSide
}

/** #87: which of two cost more in the last seven days. [key] is the sorted pair, for the 30-day rule; see [questionKey]. */
data class QuestionPair(val key: String, val a: QuestionSide, val b: QuestionSide) {
    /** The side that cost more. A tie is never asked, so there is always one. */
    val aCostMore: Boolean get() = a.amount > b.amount
}

/**
 * #87: two categories with close totals in the seven days before [today], or two shops when no
 * categories are close. Never a pair asked in the last 30 days, never a tie, and the closest pair
 * first, so the same ledger always asks the same question. Null when the week has no fair pair.
 */
fun List<Transaction>.pickQuestion(today: LocalDate, askedKeys: Set<String>, zone: TimeZone = HomeTime): QuestionPair? {
    val week = lastWeek(today, zone)
    val categories = week.mapNotNull { row -> row.categoryId?.let { it to row } }.groupBy({ it.first }, { it.second })
        .map { (id, rows) -> QuestionSide.Category(id, rows.map { it.amount }.sum(), rows.size) }
    val shops = week.mapNotNull { row -> row.counterparty?.trim()?.takeIf(String::isNotEmpty)?.let { it to row } }.groupBy({ it.first }, { it.second })
        .map { (name, rows) -> QuestionSide.Shop(name, rows.map { it.amount }.sum(), rows.size) }
    return closestPair(categories, askedKeys) ?: closestPair(shops, askedKeys)
}

/** The pair asked on a day, rebuilt from its [key] so an answered question shows the same two cards. */
fun List<Transaction>.questionFor(key: String, today: LocalDate, zone: TimeZone = HomeTime): QuestionPair? {
    val (first, second) = key.split(KEY_SEPARATOR).takeIf { it.size == 2 } ?: return null
    val week = lastWeek(today, zone)
    val a = week.side(first) ?: return null
    val b = week.side(second) ?: return null
    return QuestionPair(key, a, b)
}

/** Keys asked in the [QUESTION_REPEAT_DAYS] days up to [today]. */
fun List<PlayDay>.askedKeys(today: LocalDate): Set<String> {
    val from = today.minus(QUESTION_REPEAT_DAYS, DateTimeUnit.DAY)
    return filter { it.date in from..today }.mapNotNull { it.questionKey }.toSet()
}

private fun List<Transaction>.lastWeek(today: LocalDate, zone: TimeZone): List<Transaction> =
    filter { it.direction == Direction.Expense }
        .inside(DateRange(today.minus(DAYS_IN_WEEK, DateTimeUnit.DAY), today.minus(1, DateTimeUnit.DAY)), zone)

private fun List<Transaction>.side(id: String): QuestionSide? = when {
    id.startsWith(CATEGORY) -> id.removePrefix(CATEGORY).let { categoryId ->
        filter { it.categoryId == categoryId }.takeIf { it.isNotEmpty() }?.let { rows -> QuestionSide.Category(categoryId, rows.map { it.amount }.sum(), rows.size) }
    }

    id.startsWith(SHOP) -> id.removePrefix(SHOP).let { name ->
        filter { it.counterparty?.trim() == name }.takeIf { it.isNotEmpty() }?.let { rows -> QuestionSide.Shop(name, rows.map { it.amount }.sum(), rows.size) }
    }

    else -> null
}

private fun QuestionSide.id(): String = when (this) {
    is QuestionSide.Category -> CATEGORY + categoryId
    is QuestionSide.Shop -> SHOP + name
}

private fun closestPair(sides: List<QuestionSide>, asked: Set<String>): QuestionPair? {
    val ordered = sides.filter { it.amount > Satang.Zero }.sortedBy { it.id() }
    return ordered.indices.flatMap { i -> (i + 1 until ordered.size).map { j -> ordered[i] to ordered[j] } }
        .filter { (a, b) -> a.amount != b.amount && close(a.amount, b.amount) }
        .map { (a, b) -> QuestionPair("${a.id()}$KEY_SEPARATOR${b.id()}", a, b) }
        .filterNot { it.key in asked }
        .minWithOrNull(compareBy<QuestionPair> { gap(it) }.thenBy { it.key })
}

private fun close(a: Satang, b: Satang): Boolean {
    val larger = maxOf(a, b).toMinorUnits()
    return (larger - minOf(a, b).toMinorUnits()) * PERCENT <= larger * CLOSE_PERCENT
}

/** How far apart the two are, in parts per ten thousand of the larger, so pairs of any size compare. */
private fun gap(pair: QuestionPair): Long {
    val larger = maxOf(pair.a.amount, pair.b.amount).toMinorUnits()
    return (larger - minOf(pair.a.amount, pair.b.amount).toMinorUnits()) * PERCENT * PERCENT / larger
}

/** A pair's key, as [pickQuestion] stores it: the two sides' ids in order, apart by a separator no name holds. */
fun questionKey(first: String, second: String): String = "$first$KEY_SEPARATOR$second"
