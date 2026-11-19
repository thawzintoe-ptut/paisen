package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.shared.SharedSnapshot
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedActivityViewModelTest : ViewModelTest() {

    @Test
    fun `Changes others made after the mark are new and yours are not`() = runTest {
        val viewModel = viewModel()

        viewModel.since(1_000)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf("groceries"), state.fresh.map { it.expenseId })
        assertEquals(listOf("taxi", "water"), state.days.flatMap { it.entries }.map { it.expenseId })
    }

    @Test
    fun `With no mark nothing is new`() = runTest {
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.fresh.isEmpty())
        assertEquals(3, viewModel.uiState.value.days.flatMap { it.entries }.size)
    }

    @Test
    fun `A later mark clears the count`() = runTest {
        val viewModel = viewModel()
        viewModel.since(1_000)
        runCurrent()

        viewModel.since(5_000)
        runCurrent()

        assertTrue(viewModel.uiState.value.fresh.isEmpty())
    }

    @Test
    fun `An added expense opens that expense`() = runTest {
        val viewModel = viewModel()

        val entry = viewModel.uiState.value.days.flatMap { it.entries }.first { it.expenseId == "taxi" }
        assertEquals(SharedEntryKind.Added, entry.kind)
    }

    private fun TestScope.viewModel(): SharedActivityViewModel {
        val repository = FakeSharedRepository(flat12)
        repository.snapshot.value = SharedSnapshot(
            groups = listOf(flat12),
            people = emptyList(),
            expenseVersions = listOf(
                expense("groceries", payer = "aye", "me" to 100, "aye" to 100, createdAt = 2_000),
                expense("taxi", payer = "me", "me" to 100, "aye" to 100, createdAt = 1_500),
                expense("water", payer = "aye", "me" to 100, "aye" to 100, createdAt = 500),
            ),
            payments = emptyList(),
            links = emptyMap(),
        )
        val viewModel = SharedActivityViewModel(groupId = flat12.id, repository = repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }
}
