package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.inAppLanguage
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.Language
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AddExpenseViewModelTest : ViewModelTest() {

    @AfterTest
    fun backToEnglish() {
        AppText.use(Language.English)
    }

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

    /** The title syncs to every member, so it must not carry the writer's language. */
    @Test
    fun `an untitled expense saved in Burmese reaches the group under the installed English name`() = runTest {
        AppText.use(Language.Burmese)

        assertEquals("Food & drink", titleSavedWithNoDescription(Category.seed()))
    }

    @Test
    fun `an untitled expense in a renamed category keeps the name the reader typed`() = runTest {
        AppText.use(Language.Burmese)
        val renamed = Category.seed().map { if (it.id == "food") it.copy(name = "Lunch out") else it }

        assertEquals("Lunch out", titleSavedWithNoDescription(renamed))
    }

    /** The categories go in as the app hands them over: installed names already in the app's language. */
    private fun TestScope.titleSavedWithNoDescription(stored: List<Category>): String {
        val repository = FakeSharedRepository()
        val categories = stored.map(Category::inAppLanguage)
        val viewModel = AddExpenseViewModel(groupId = "g1", expenseId = null, repository = repository, categories = categories)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.setAmount("240")
        runCurrent()

        viewModel.save {}
        repository.gate.complete(Unit)
        runCurrent()

        return repository.expenses.single().description
    }
}
