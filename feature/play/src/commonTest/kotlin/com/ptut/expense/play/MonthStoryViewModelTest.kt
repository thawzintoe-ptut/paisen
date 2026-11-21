package com.ptut.expense.play

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.play.FakePlayBook
import com.ptut.expense.domain.play.GetMonthStoryUseCase
import com.ptut.expense.domain.play.MarkStorySeenUseCase
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
import kotlin.test.assertTrue

class MonthStoryViewModelTest : ViewModelTest() {

    @Test
    fun `reading to the last card marks the story read and the shared card hides amounts by default`() = runTest {
        val book = FakePlayBook()
        val rows = listOf(spendOn("a", LocalDate(2026, 10, 3), 100_000, shop = "Lawson"), spendOn("b", LocalDate(2026, 11, 3), 90_000, shop = "Lawson"))
        val story = MonthStoryViewModel("2026-11", GetMonthStoryUseCase(book, ObserveTransactionsUseCase(FakeLedger(rows)), GetHomeTodayUseCase(clockAt(LocalDate(2026, 12, 3)))), MarkStorySeenUseCase(book), shareAmounts = flowOf(false))
        backgroundScope.launch { story.uiState.collect {} }
        runCurrent()
        val first = assertNotNull(story.uiState.value)
        assertEquals("What you spent", first.card?.title)
        assertEquals("฿900.00", first.card?.figure)
        assertFalse(first.shareAmounts)

        while (story.next()) runCurrent()
        runCurrent()

        assertTrue(book.months.value.single().storySeenAtEpochMillis != null)
    }
}
