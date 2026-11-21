package com.ptut.expense.play

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.play.FakePlayBook
import com.ptut.expense.domain.play.ObserveStampBookUseCase
import com.ptut.expense.domain.play.Stamp
import com.ptut.expense.domain.play.StampKind
import com.ptut.expense.domain.play.stampId
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import com.ptut.expense.ui.play.StampTone
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Instant

class StampBookViewModelTest : ViewModelTest() {

    /** Saturday 7 November 2026; the 1st is a Sunday, so the month starts with six blanks. */
    private val now = LocalDateTime(2026, 11, 7, 12, 0).toInstant(BangkokTime)

    @Test
    fun `the calendar marks days with nothing spent and leaves the rest plain`() = runTest {
        val rows = listOf(spend(3), spend(5))
        val book = viewModel(rows)
        backgroundScope.launch { book.uiState.collect {} }
        runCurrent()

        val state = assertNotNull(book.uiState.value)
        // The ledger starts on the 3rd, so the 1st and 2nd are not called no-spend days.
        assertEquals("3 days with nothing spent this month", state.noSpendLine)
        assertEquals(6, state.calendar.takeWhile { it.day == null }.size)
        assertEquals(listOf("4", "6", "7"), state.calendar.filter { it.noSpend }.map { it.day })
        assertEquals(true, state.calendar.single { it.day == "7" }.today)
    }

    @Test
    fun `each set shows earned stamps and empty slots and the next set is the first unfinished`() = runTest {
        val monday = Stamp(stampId(StampKind.Monday, "2026-11-02"), StampKind.Monday, "2026-11-02", LocalDate(2026, 11, 2), revealedAtEpochMillis = 1)
        val book = viewModel(emptyList(), listOf(monday))
        backgroundScope.launch { book.uiState.collect {} }
        runCurrent()

        val state = assertNotNull(book.uiState.value)
        val everyday = state.sets.first()
        assertEquals(StampTone.Ink, everyday.stamps.first().tone)
        assertEquals(6, everyday.stamps.count { it.tone == StampTone.Empty })
        assertEquals("Next set: Every day", state.nextSetTitle)
        assertEquals("Every day: 1 of 7", state.nextSet?.line)
    }

    private fun viewModel(rows: List<Transaction>, stamps: List<Stamp> = emptyList()) = StampBookViewModel(
        ObserveStampBookUseCase(
            book = FakePlayBook(stamps = stamps),
            transactions = ObserveTransactionsUseCase(FakeLedger(rows)),
            homeToday = GetHomeTodayUseCase(
                object : Clock {
                    override fun now(): Instant = now
                },
            ),
        ),
    )

    private fun spend(day: Int) = Transaction(
        id = "t$day",
        occurredAtEpochMillis = LocalDateTime(2026, 11, day, 12, 0).toInstant(BangkokTime).toEpochMilliseconds(),
        amount = Satang(5_000),
        direction = Direction.Expense,
        categoryId = "food",
        counterparty = "Cafe",
    )
}
