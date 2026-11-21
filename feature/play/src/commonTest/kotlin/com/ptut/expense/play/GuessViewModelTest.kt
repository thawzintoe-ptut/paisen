package com.ptut.expense.play

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.play.FakePlayBook
import com.ptut.expense.domain.play.PlayWeek
import com.ptut.expense.domain.play.RevealGuessUseCase
import com.ptut.expense.domain.play.StampKind
import com.ptut.expense.domain.play.StartGuessUseCase
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class GuessViewModelTest : ViewModelTest() {

    /** Guessing the week of 16 Nov, with 19 Oct to 9 Nov behind it: 1,000 / 1,000 / 1,000 / 1,000. */
    private val monday = LocalDate(2026, 11, 16)
    private val earlier = listOf(spend(20, 100_000, month = 10), spend(27, 100_000, month = 10), spend(3, 100_000), spend(10, 100_000))
    private val clock = object : Clock {
        override fun now(): Instant = LocalDateTime(2026, 11, 23, 9, 0).toInstant(BangkokTime)
    }

    private fun TestScope.guess(book: FakePlayBook, weekSpend: Long): GuessViewModel {
        val ledger = ObserveTransactionsUseCase(FakeLedger(earlier + spend(18, weekSpend)))
        val guess = GuessViewModel(
            monday = monday.toString(),
            startGuess = StartGuessUseCase(book, ledger),
            revealGuess = RevealGuessUseCase(book, ledger, GetHomeTodayUseCase(clock)),
        )
        runCurrent()
        return guess
    }

    @Test
    fun `PaiSeN's guess is stored before the slider shows`() = runTest {
        val book = FakePlayBook()

        val state = assertIs<GuessUiState.Guessing>(guess(book, weekSpend = 120_000).uiState.value)

        assertEquals(Satang(100_000), book.weeks.value.single().paisenGuess)
        assertEquals("1,000", state.digits)
        assertEquals("Your first guess. PaiSeN has guessed already.", state.record)
    }

    @Test
    fun `a closer guess earns Beat PaiSeN and the reveal rolls from it`() = runTest {
        val book = FakePlayBook()
        val guess = guess(book, weekSpend = 120_000)

        guess.nudge(2)
        guess.reveal()
        runCurrent()

        val state = assertIs<GuessUiState.Revealed>(guess.uiState.value)
        assertEquals("1,200", state.digits)
        assertEquals("1,200", state.startFrom)
        assertTrue(state.you.closer)
        assertEquals("spot on", state.you.off)
        assertEquals(listOf(StampKind.BeatPaiSeN), book.stamps.value.map { it.kind })
    }

    @Test
    fun `a tie goes to PaiSeN and earns nothing`() = runTest {
        val book = FakePlayBook()
        val guess = guess(book, weekSpend = 110_000)

        guess.nudge(2)
        guess.reveal()
        runCurrent()

        val state = assertIs<GuessUiState.Revealed>(guess.uiState.value)
        assertTrue(state.paisen.closer)
        assertEquals("PaiSeN was closer this time. Nothing is lost.", state.outcome)
        assertTrue(book.stamps.value.isEmpty())
    }

    @Test
    fun `the record shows the best closeness and the weeks played`() = runTest {
        val played = PlayWeek(LocalDate(2026, 11, 9), guess = Satang(90_000), total = Satang(100_000))

        val state = assertIs<GuessUiState.Guessing>(guess(FakePlayBook(weeks = listOf(played)), weekSpend = 120_000).uiState.value)

        assertEquals("Your best: 90% · 1 week played", state.record)
    }

    @Test
    fun `a week already guessed opens on its reveal`() = runTest {
        val book = FakePlayBook(weeks = listOf(PlayWeek(monday, paisenGuess = Satang(100_000), guess = Satang(110_000), total = Satang(120_000))))

        assertIs<GuessUiState.Revealed>(guess(book, weekSpend = 120_000).uiState.value)
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
