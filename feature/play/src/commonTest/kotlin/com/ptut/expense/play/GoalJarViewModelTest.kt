package com.ptut.expense.play

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.play.ArchiveGoalUseCase
import com.ptut.expense.domain.play.FakePlayBook
import com.ptut.expense.domain.play.GoalDay
import com.ptut.expense.domain.play.ObserveGoalJarUseCase
import com.ptut.expense.domain.play.SaveGoalUseCase
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GoalJarViewModelTest : ViewModelTest() {

    private val today = LocalDate(2026, 11, 25)
    private val book = FakePlayBook()

    private fun TestScope.jar(name: String? = null, target: String? = null, rows: List<Transaction> = emptyList()): GoalJarViewModel {
        val ledger = ObserveTransactionsUseCase(FakeLedger(rows))
        val homeToday = GetHomeTodayUseCase(clockAt(today))
        val jar = GoalJarViewModel(null, name, target, ObserveGoalJarUseCase(book, ledger, homeToday), SaveGoalUseCase(book, homeToday, clockAt(today)), ArchiveGoalUseCase(book, clockAt(today)), flowOf(Category.seed()))
        backgroundScope.launch { jar.uiState.collect {} }
        runCurrent()
        return jar
    }

    @Test
    fun `a trip's estimate opens the form filled and saving starts the jar today`() = runTest {
        val jar = jar(name = "Chiang Mai", target = "1200.00")
        assertEquals(true, assertIs<GoalJarUiState.Editing>(jar.uiState.value).canSave)

        jar.save()
        runCurrent()

        val goal = book.goals.value.single()
        assertEquals(Satang(120_000), goal.target)
        assertEquals(today, goal.start)
        assertIs<GoalJarUiState.Jar>(jar.uiState.value)
    }

    @Test
    fun `with no counted day the jar says how it fills`() = runTest {
        val jar = jar(name = "Trip", target = "500", rows = listOf(spendOn("a", today, 1_000)))
        jar.save()
        runCurrent()

        assertEquals("The jar fills from days you count on Today. Spend under a usual day, then tap Count it.", assertIs<GoalJarUiState.Jar>(jar.uiState.value).pace)
    }

    @Test
    fun `a jar with no name or amount cannot be saved`() = runTest {
        val jar = jar()
        jar.setName("Trip")
        runCurrent()

        assertEquals(false, assertIs<GoalJarUiState.Editing>(jar.uiState.value).canSave)
        jar.save()
        runCurrent()
        assertEquals(emptyList(), book.goals.value)
    }

    @Test
    fun `skipping a habit shows its own date`() = runTest {
        val rows = listOf(
            spendOn("in", LocalDate(2026, 11, 2), 400_000, category = "income", direction = Direction.Income),
            spendOn("food1", LocalDate(2026, 11, 10), 100_000),
            spendOn("food2", LocalDate(2026, 11, 17), 100_000),
        )
        val jar = jar(name = "Trip", target = "100000", rows = rows)
        jar.save()
        runCurrent()

        jar.whatIf("food")
        runCurrent()

        val state = assertIs<GoalJarUiState.Jar>(jar.uiState.value)
        assertEquals("food", state.chosen)
        assertEquals("Skip Food & drink", state.whatIf.single().label)
    }

    @Test
    fun `a jar already holding its amount says it is full`() = runTest {
        val jar = jar(name = "Trip", target = "500")
        jar.save()
        runCurrent()
        book.answerDay(GoalDay(book.goals.value.single().id, today, Satang(60_000), atEpochMillis = 0))
        runCurrent()

        assertEquals("The jar is full.", assertIs<GoalJarUiState.Jar>(jar.uiState.value).pace)
    }
}
