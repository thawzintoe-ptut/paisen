package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Category
import com.ptut.expense.ui.ToastMessages
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

    /** #6: a new repeat opens on next month's first day; only what is typed or picked after that asks. */
    @Test
    fun `leaving asks first only once the repeat differs from how it opened`() = runTest {
        val viewModel = RepeatExpenseViewModel(
            groupId = "g1",
            ruleId = null,
            repository = FakeSharedRepository(),
            categories = Category.seed(),
            toasts = ToastMessages(),
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        assertFalse(viewModel.ready().changed)

        viewModel.setAmount("18000")
        runCurrent()
        assertTrue(viewModel.ready().changed)

        viewModel.setAmount("")
        runCurrent()
        assertFalse(viewModel.ready().changed)

        viewModel.setRemind(false)
        runCurrent()
        assertTrue(viewModel.ready().changed)
    }

    private fun RepeatExpenseViewModel.ready() = assertIs<RepeatExpenseUiState.Ready>(uiState.value)
}
