package com.ptut.expense.domain.play

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.inWeek
import com.ptut.expense.domain.minorPerWhole
import com.ptut.expense.domain.mondayOfWeek
import com.ptut.expense.domain.total
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/** How many earlier weeks PaiSeN reads (#86). */
private const val GUESS_WEEKS = 4

/** Fewer earlier weeks than this, and PaiSeN does not guess: the same floor as `weeklyCheckIn`. */
private const val GUESS_MIN_WEEKS = 2

/** The guess moves in steps of 100 of the home money (#86: ฿100). */
private const val STEP_WHOLE = 100L

/** The slider runs from half the quietest week to half again over the busiest. */
private const val RANGE_LOW_PERCENT = 50L
private const val RANGE_HIGH_PERCENT = 150L

/** PaiSeN's guess and what "How PaiSeN guessed" lists: the earlier weeks, oldest first, and the trend added. */
data class PaisenGuess(val amount: Satang, val weeks: List<Satang>, val trend: Satang)

/**
 * #86: the spending of the week starting [monday], guessed from up to four weeks before it: their mean
 * plus half the week-on-week trend, never below nothing, to the nearest step. Null with fewer than two
 * earlier weeks in the ledger.
 */
fun List<Transaction>.paisenGuess(monday: LocalDate, zone: TimeZone = HomeTime): PaisenGuess? {
    val first = minOfOrNull { it.occurredAtEpochMillis }?.dateIn(zone)?.mondayOfWeek() ?: return null
    val expenses = filter { it.direction == Direction.Expense }
    val weeks = (GUESS_WEEKS downTo 1)
        .map { monday.minus(it, DateTimeUnit.WEEK) }
        .filter { it >= first }
        .map { expenses.inWeek(it, zone).total() }
    if (weeks.size < GUESS_MIN_WEEKS) return null
    val mean = weeks.sumOf { it.toMinorUnits() } / weeks.size
    val trend = (weeks.last().toMinorUnits() - weeks.first().toMinorUnits()) / (weeks.size - 1) / 2
    return PaisenGuess(amount = Satang.ofMinorUnits((mean + trend).coerceAtLeast(0L)).toNearestStep(), weeks = weeks, trend = Satang.ofMinorUnits(trend))
}

/** #86: what the week starting [monday] came to, the number the guesses are measured against. */
fun List<Transaction>.weekSpend(monday: LocalDate, zone: TimeZone = HomeTime): Satang =
    filter { it.direction == Direction.Expense }.inWeek(monday, zone).total()

/** The slider's ends and its step, from the weeks PaiSeN read. */
data class GuessRange(val min: Satang, val max: Satang, val step: Satang)

fun PaisenGuess.range(): GuessRange {
    val step = guessStep()
    val low = weeks.minOf { it.toMinorUnits() } * RANGE_LOW_PERCENT / PERCENT
    val high = maxOf(weeks.maxOf { it.toMinorUnits() }, amount.toMinorUnits()) * RANGE_HIGH_PERCENT / PERCENT
    val min = low / step.toMinorUnits() * step.toMinorUnits()
    val max = maxOf(min + step.toMinorUnits(), ceilTo(high, step.toMinorUnits()))
    return GuessRange(Satang.ofMinorUnits(min), Satang.ofMinorUnits(max), step)
}

/** One step of the slider, ฿100 in baht. */
fun guessStep(): Satang = Satang.ofMinorUnits(STEP_WHOLE * minorPerWhole())

/** How a reveal came out. A tie goes to PaiSeN, so the reader wins only by being closer. */
data class GuessResult(val total: Satang, val you: Satang, val paisen: Satang, val youCloser: Boolean, val closenessPercent: Int)

fun guessResult(total: Satang, you: Satang, paisen: Satang): GuessResult {
    val youOff = (total - you).abs()
    val paisenOff = (total - paisen).abs()
    return GuessResult(total, you, paisen, youCloser = youOff < paisenOff, closenessPercent = closeness(total, youOff))
}

/** "Your record": the best closeness and the weeks played. No win count anywhere (D4). */
data class GuessRecord(val bestPercent: Int?, val weeksPlayed: Int)

fun List<PlayWeek>.guessRecord(): GuessRecord {
    val played = mapNotNull { week ->
        val guess = week.guess ?: return@mapNotNull null
        val total = week.total ?: return@mapNotNull null
        closeness(total, (total - guess).abs())
    }
    return GuessRecord(bestPercent = played.maxOrNull(), weeksPlayed = played.size)
}

/** 100 is spot on; a guess off by the whole total or more is 0. */
private fun closeness(total: Satang, off: Satang): Int {
    val whole = total.toMinorUnits()
    if (whole <= 0L) return if (off == Satang.Zero) PERCENT.toInt() else 0
    return (PERCENT - off.toMinorUnits() * PERCENT / whole).coerceIn(0L, PERCENT).toInt()
}

private fun Satang.toNearestStep(): Satang {
    val step = guessStep().toMinorUnits()
    return Satang.ofMinorUnits((toMinorUnits() + step / 2) / step * step)
}

private fun ceilTo(value: Long, step: Long): Long = (value + step - 1) / step * step
