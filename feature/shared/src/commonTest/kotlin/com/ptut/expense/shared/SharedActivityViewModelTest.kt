package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.shared.GetActivityUseCase
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedActivityViewModelTest : ViewModelTest() {

    @Test
    fun `changes others made after the mark are new and yours are not`() = runTest {
        val viewModel = viewModel()

        viewModel.since(1_000)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf("groceries"), state.fresh.map { it.expenseId })
        assertEquals(listOf("taxi", "water"), state.days.flatMap { it.entries }.map { it.expenseId })
    }

    @Test
    fun `with no mark nothing is new`() = runTest {
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.fresh.isEmpty())
        assertEquals(3, viewModel.uiState.value.days.flatMap { it.entries }.size)
    }

    @Test
    fun `a later mark clears the count`() = runTest {
        val viewModel = viewModel()
        viewModel.since(1_000)
        runCurrent()

        viewModel.since(5_000)
        runCurrent()

        assertTrue(viewModel.uiState.value.fresh.isEmpty())
    }

    @Test
    fun `an added expense opens that expense`() = runTest {
        val viewModel = viewModel()

        val entry = viewModel.uiState.value.days.flatMap { it.entries }.first { it.expenseId == "taxi" }
        assertEquals(SharedEntryKind.Added, entry.kind)
    }

    @Test
    fun `the same day six years apart reads alike and stays two days`() = runTest {
        val viewModel = viewModel(
            expense("now", payer = "me", "me" to 100, createdAt = noon(2026)),
            expense("then", payer = "me", "me" to 100, createdAt = noon(2020)),
        )

        val days = viewModel.uiState.value.days
        assertEquals(1, days.map { it.label }.distinct().size)
        assertEquals(listOf(LocalDate(2026, 11, 12), LocalDate(2020, 11, 12)), days.map { it.date })
    }

    private fun TestScope.viewModel(
        vararg expenses: SharedExpense = arrayOf(
            expense("groceries", payer = "aye", "me" to 100, "aye" to 100, createdAt = 2_000),
            expense("taxi", payer = "me", "me" to 100, "aye" to 100, createdAt = 1_500),
            expense("water", payer = "aye", "me" to 100, "aye" to 100, createdAt = 500),
        ),
    ): SharedActivityViewModel {
        val repository = FakeSharedRepository(flat12)
        repository.snapshot.value = SharedSnapshot(
            groups = listOf(flat12),
            people = emptyList(),
            expenseVersions = expenses.toList(),
            payments = emptyList(),
            links = emptyMap(),
        )
        val viewModel = SharedActivityViewModel(
            groupId = flat12.id,
            observeSnapshot = ObserveSharedSnapshotUseCase(repository),
            getActivity = GetActivityUseCase(GetHomeTodayUseCase()),
            work = Dispatchers.Main,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }
}
