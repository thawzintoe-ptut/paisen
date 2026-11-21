package com.ptut.expense.play

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.play.Challenge
import com.ptut.expense.domain.play.ChallengeLevel
import com.ptut.expense.domain.play.FakePlayBook
import com.ptut.expense.domain.play.GetChallengeChoicesUseCase
import com.ptut.expense.domain.play.PickChallengeUseCase
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChallengeViewModelTest : ViewModelTest() {

    private val today = LocalDate(2026, 11, 25)
    private val rows = listOf(spendOn("a", LocalDate(2026, 11, 10), 100_000), spendOn("b", LocalDate(2026, 11, 17), 100_000), spendOn("c", LocalDate(2026, 11, 24), 30_000))

    @Test
    fun `a pick is stored for the week and shows with its progress`() = runTest {
        val book = FakePlayBook()
        val ledger = ObserveTransactionsUseCase(FakeLedger(rows))
        val homeToday = GetHomeTodayUseCase(clockAt(today))
        val challenge = ChallengeViewModel(GetChallengeChoicesUseCase(book, ledger, homeToday), PickChallengeUseCase(book, ledger, homeToday), flowOf(Category.seed()))
        backgroundScope.launch { challenge.uiState.collect {} }
        runCurrent()
        assertEquals(listOf("Easy", "Medium", "Hard"), assertNotNull(challenge.uiState.value).groups.single().options.map { it.levelLabel })

        challenge.pick("food", ChallengeLevel.Medium)
        runCurrent()

        assertEquals(Challenge("food", ChallengeLevel.Medium, Satang(85_000)), book.weeks.value.single().challenge)
        val state = assertNotNull(challenge.uiState.value)
        assertEquals("Food & drink at most ฿850.00 this week", state.current?.line)
        assertEquals("฿300.00 so far · 5 days left", state.current?.progress)
        assertTrue(state.groups.single().options.single { it.picked }.level == ChallengeLevel.Medium)
    }
}
