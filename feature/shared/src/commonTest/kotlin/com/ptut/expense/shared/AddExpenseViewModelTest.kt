package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Category
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AddExpenseViewModelTest : ViewModelTest() {

    /** #6: the second tap lands while the first save is still writing. */
    @Test
    fun `two fast taps on Save add one expense`() = runTest {
        val repository = FakeSharedRepository()
        val viewModel = AddExpenseViewModel(groupId = "g1", expenseId = null, repository = repository, categories = Category.seed())
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.setAmount("240")
        viewModel.setDescription("Groceries")
        runCurrent()

        var closed = 0
        viewModel.save { closed++ }
        viewModel.save { closed++ }
        runCurrent()
        repository.gate.complete(Unit)
        runCurrent()

        assertEquals(1, repository.expenses.size)
        assertEquals(1, closed)
    }
}
