package com.ptut.expense.play

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.play.FakePlayBook
import com.ptut.expense.domain.play.GetMonthCloseUseCase
import com.ptut.expense.domain.play.SealMonthUseCase
import com.ptut.expense.domain.play.StampKind
import com.ptut.expense.domain.slip.ObserveQueuedSlipsUseCase
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloseMonthViewModelTest : ViewModelTest() {

    @Test
    fun `three steps then the seal which earns Month closed once`() = runTest {
        val book = FakePlayBook()
        val ledger = FakeLedger(listOf(spendOn("a", LocalDate(2026, 11, 3), 12_000, shop = "Lawson"), spendOn("b", LocalDate(2026, 11, 3), 12_000, shop = "Lawson")))
        val close = CloseMonthViewModel(
            "2026-11",
            GetMonthCloseUseCase(book, ObserveTransactionsUseCase(ledger), ObserveQueuedSlipsUseCase(ledger, flowOf(emptySet()))),
            SealMonthUseCase(book, GetHomeTodayUseCase(clockAt(LocalDate(2026, 11, 30))), clockAt(LocalDate(2026, 11, 30))),
        )
        backgroundScope.launch { close.uiState.collect {} }
        runCurrent()
        assertEquals("No slips waiting", assertNotNull(close.uiState.value).step?.lines?.single())

        close.next()
        runCurrent()
        assertEquals("Maybe twice: Lawson, ฿120.00", assertNotNull(close.uiState.value).step?.lines?.single())
        close.next()
        runCurrent()
        close.seal()
        close.seal()
        runCurrent()

        val state = assertNotNull(close.uiState.value)
        assertNull(state.step)
        assertEquals(listOf(StampKind.MonthClosed), book.stamps.value.map { it.kind })
    }

    @Test
    fun `slips from the next month do not block the seal`() = runTest {
        val close = closeWith(queued = listOf(slipOn("dec", LocalDate(2026, 12, 2))))
        backgroundScope.launch { close.uiState.collect {} }
        runCurrent()
        assertEquals("No slips waiting", assertNotNull(close.uiState.value).step?.lines?.single())

        close.next()
        close.next()
        runCurrent()

        val state = assertNotNull(close.uiState.value)
        assertTrue(state.canSeal)
        assertNull(state.sealBlocked)
    }

    @Test
    fun `a slip inside the month blocks the seal and says why`() = runTest {
        val close = closeWith(queued = listOf(slipOn("a", LocalDate(2026, 11, 20)), slipOn("b", LocalDate(2026, 11, 21)), slipOn("dec", LocalDate(2026, 12, 2))))
        backgroundScope.launch { close.uiState.collect {} }
        close.next()
        close.next()
        runCurrent()

        val state = assertNotNull(close.uiState.value)
        assertFalse(state.canSeal)
        assertEquals("Check the 2 November slips first", state.sealBlocked)
    }

    @Test
    fun `a row both risen and large is listed once`() = runTest {
        val rows = listOf(spendOn("old", LocalDate(2026, 10, 5), 10_000, shop = "Shopee")) +
            (1..5).map { spendOn("s$it", LocalDate(2026, 11, it), 10_000) } +
            spendOn("big", LocalDate(2026, 11, 9), 129_000, shop = "Shopee")
        val close = closeWith(rows = rows)
        backgroundScope.launch { close.uiState.collect {} }
        close.next()
        runCurrent()

        assertEquals(listOf("Shopee · ฿1,290.00 · more than last month, large"), assertNotNull(close.uiState.value).step?.lines)
    }

    private fun closeWith(rows: List<Transaction> = emptyList(), queued: List<QueuedSlip> = emptyList()): CloseMonthViewModel {
        val book = FakePlayBook()
        val ledger = FakeLedger(rows, queued = queued)
        return CloseMonthViewModel(
            "2026-11",
            GetMonthCloseUseCase(book, ObserveTransactionsUseCase(ledger), ObserveQueuedSlipsUseCase(ledger, flowOf(emptySet()))),
            SealMonthUseCase(book, GetHomeTodayUseCase(clockAt(LocalDate(2026, 12, 2))), clockAt(LocalDate(2026, 12, 2))),
        )
    }

    private fun slipOn(id: String, day: LocalDate) = spendOn(id, day, 0).let {
        QueuedSlip(id, "Shop", it.occurredAtEpochMillis, "", null, ReviewReason.AmountUnclear, it.occurredAtEpochMillis)
    }
}
