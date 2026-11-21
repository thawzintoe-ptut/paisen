package com.ptut.expense.domain.play

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaisenGuessTest {

    /** The week being guessed starts Monday 23 November; the four before it start 26 Oct to 16 Nov. */
    private val monday = LocalDate(2026, 11, 23)

    @Test
    fun `fewer than two earlier weeks gives no guess`() {
        assertNull(listOf(spend(16, 300_000)).paisenGuess(monday, BangkokTime))
    }

    @Test
    fun `the guess is the mean plus half the trend to the nearest step`() {
        // 2,000 / 2,400 / 2,800 / 3,200: mean 2,600, trend 400 a week, half of it 200.
        val rows = listOf(spend(26, 200_000, month = 10), spend(2, 240_000), spend(9, 280_000), spend(16, 320_000))

        val guess = rows.paisenGuess(monday, BangkokTime)

        assertEquals(Satang(280_000), guess?.amount)
        assertEquals(listOf(Satang(200_000), Satang(240_000), Satang(280_000), Satang(320_000)), guess?.weeks)
        assertEquals(Satang(20_000), guess?.trend)
    }

    @Test
    fun `the range runs from half the quietest week to half again over the busiest in 100 steps`() {
        val range = PaisenGuess(Satang(280_000), listOf(Satang(200_000), Satang(320_050)), Satang(0)).range()

        assertEquals(Satang(100_000), range.min)
        assertEquals(Satang(490_000), range.max)
        assertEquals(Satang(10_000), range.step)
    }

    @Test
    fun `the closer guess wins and a tie goes to PaiSeN`() {
        assertTrue(guessResult(total = Satang(300_000), you = Satang(290_000), paisen = Satang(250_000)).youCloser)
        assertFalse(guessResult(total = Satang(300_000), you = Satang(310_000), paisen = Satang(290_000)).youCloser)
    }

    @Test
    fun `closeness is how near the guess came as a percent`() {
        assertEquals(90, guessResult(Satang(100_000), Satang(110_000), Satang(0)).closenessPercent)
        assertEquals(0, guessResult(Satang(100_000), Satang(300_000), Satang(0)).closenessPercent)
    }

    @Test
    fun `the record keeps the best closeness and the weeks played never wins`() {
        val weeks = listOf(
            PlayWeek(LocalDate(2026, 11, 9), guess = Satang(90_000), total = Satang(100_000)),
            PlayWeek(LocalDate(2026, 11, 16), guess = Satang(100_000), total = Satang(100_000)),
            PlayWeek(LocalDate(2026, 11, 23), paisenGuess = Satang(100_000)),
        )

        assertEquals(GuessRecord(bestPercent = 100, weeksPlayed = 2), weeks.guessRecord())
    }

    private fun spend(day: Int, minor: Long, month: Int = 11) = Transaction(
        id = "t$month-$day",
        occurredAtEpochMillis = LocalDateTime(2026, month, day, 12, 0).toInstant(BangkokTime).toEpochMilliseconds(),
        amount = Satang(minor),
        direction = Direction.Expense,
        categoryId = "food",
        counterparty = "Cafe",
    )
}
