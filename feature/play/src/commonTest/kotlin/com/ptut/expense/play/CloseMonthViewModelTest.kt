package com.ptut.expense.play

import com.ptut.expense.ViewModelTest
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
}
