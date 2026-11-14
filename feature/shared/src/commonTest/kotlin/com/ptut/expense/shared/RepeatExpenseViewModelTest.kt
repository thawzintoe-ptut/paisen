package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Category
import com.ptut.expense.ui.ToastMessages
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatExpenseViewModelTest : ViewModelTest() {

    /** #6: the second tap lands while the first repeat is still writing. */
    @Test
    fun `two fast taps on Save set up one repeat`() = runTest {
        val repository = FakeSharedRepository()
        val viewModel = RepeatExpenseViewModel(
            groupId = "g1",
            ruleId = null,
            repository = repository,
            categories = Category.seed(),
            toasts = ToastMessages(),
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.setAmount("18000")
        runCurrent()

        viewModel.save(allowed = true)
        viewModel.save(allowed = true)
        runCurrent()
        repository.gate.complete(Unit)
        runCurrent()

        assertEquals(1, repository.repeats.size)
    }
}
