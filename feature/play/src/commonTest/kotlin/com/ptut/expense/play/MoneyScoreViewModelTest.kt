package com.ptut.expense.play

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.play.FakePlayBook
import com.ptut.expense.domain.play.GetMoneyScoreUseCase
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MoneyScoreViewModelTest : ViewModelTest() {

    private val today = LocalDate(2026, 11, 25)

    private fun score(rows: List<Transaction>) =
        MoneyScoreViewModel(GetMoneyScoreUseCase(FakePlayBook(), ObserveTransactionsUseCase(FakeLedger(rows)), GetHomeTodayUseCase(clockAt(today))))

    @Test
    fun `income kept over spending scores that part in full`() = runTest {
        val score = score(listOf(spendOn("in", today, 100_000, category = "income", direction = Direction.Income), spendOn("out", today, 50_000)))
        backgroundScope.launch { score.uiState.collect {} }
        runCurrent()

        val state = assertNotNull(score.uiState.value)
        assertEquals("100", state.score)
        assertEquals("40 of 40", state.parts.single().points)
        assertEquals("Full", state.parts.single().gain)
    }

    @Test
    fun `nothing to score says what is needed`() = runTest {
        val score = score(emptyList())
        backgroundScope.launch { score.uiState.collect {} }
        runCurrent()

        assertEquals("A score shows once there are slips checked or income recorded.", assertNotNull(score.uiState.value).none)
    }
}
