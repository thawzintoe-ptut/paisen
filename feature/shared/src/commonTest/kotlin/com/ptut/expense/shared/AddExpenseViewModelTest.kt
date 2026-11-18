package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.inAppLanguage
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.Language
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

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

    /** #186: the amount stays History's, the category can change here and is saved with the expense. */
    @Test
    fun `an expense you paid from History takes a new category and keeps its amount`() = runTest {
        val viewModel = editingYourLinkedDinner()
        viewModel.model.setAmount("999")
        viewModel.model.setCategory("transport")
        runCurrent()

        val repository = viewModel.savedTo
        viewModel.model.save {}
        repository.gate.complete(Unit)
        runCurrent()

        val saved = repository.expenses.single()
        assertEquals("transport", saved.categoryId)
        assertEquals(30_000L, saved.amountMinor)
    }

    @Test
    fun `the locked line no longer sends a category change to History`() = runTest {
        val viewModel = editingYourLinkedDinner()

        assertEquals("From your History. Change the amount or date there.", viewModel.model.ready().lockedLine)
    }

    private class Editing(val model: AddExpenseViewModel, val savedTo: FakeSharedRepository)

    private fun TestScope.editingYourLinkedDinner(): Editing {
        val dinner = flat12.dinner(paidBy = "me", wholeMinor = 30_000).copy(categoryId = "food")
        val repository = FakeSharedRepository()
        repository.snapshot.value = repository.snapshot.value.copy(expenseVersions = listOf(dinner), links = mapOf(dinner.id to "tx-1"))
        val viewModel = AddExpenseViewModel(groupId = "g1", expenseId = dinner.id, repository = repository, categories = Category.seed())
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return Editing(viewModel, repository)
    }

    /** #48: the home stays baht here, and the group's kyat writes every amount. */
    @Test
    fun `a new expense in a kyat group starts in kyat with no rate`() = runTest {
        val viewModel = viewModel(FakeSharedRepository(flat12.copy(currency = "MMK")))
        viewModel.setAmount("9000")
        runCurrent()

        val ready = viewModel.ready()
        assertEquals("MMK", ready.form.currency.code)
        assertNull(ready.rateUnitLabel)
        assertNull(ready.groupMoneyLine)
        assertEquals("K4,500 each · you are owed K4,500", ready.resultLine)
    }

    @Test
    fun `a dollar expense in a kyat group converts into kyat`() = runTest {
        val repository = FakeSharedRepository(flat12.copy(currency = "MMK"))
        val viewModel = viewModel(repository)
        viewModel.setCurrency(Currencies.of("USD"))
        viewModel.setAmount("10")
        viewModel.setRate("3500")
        runCurrent()

        val ready = viewModel.ready()
        assertEquals("K per 1 USD", ready.rateUnitLabel)
        assertEquals("≈ K35,000 at K3500 per $, saved with the expense", ready.groupMoneyLine)
        assertEquals("$5.00 each · you are owed K17,500", ready.resultLine)

        viewModel.save {}
        repository.gate.complete(Unit)
        runCurrent()
        val saved = repository.expenses.single()
        assertEquals("USD", saved.currency)
        assertEquals(1_000L, saved.amountMinor)
        assertEquals(Rate.parse("3500"), saved.rate)
    }

    /** The rate line names the group's money, since the home's baht is not what the group counts in. */
    @Test
    fun `a baht expense in a kyat group asks for the kyat rate and never for baht`() = runTest {
        val viewModel = viewModel(FakeSharedRepository(flat12.copy(currency = "MMK")))
        viewModel.setCurrency(Currencies.Thb)
        viewModel.setAmount("100")
        runCurrent()

        val ready = viewModel.ready()
        assertEquals("K per 1 THB", ready.rateUnitLabel)
        assertEquals("Type the MMK you got for 1 ฿", ready.groupMoneyLine)
        assertEquals("Type the MMK rate to save", ready.hint)
    }

    /** A stuck key, not real money: the screen stays open and Save stays off. */
    @Test
    fun `an amount and rate past what a Long holds keep Save off and do not crash`() = runTest {
        val viewModel = viewModel(FakeSharedRepository())
        viewModel.setCurrency(Currencies.of("USD"))
        viewModel.setAmount("1" + "0".repeat(12))
        viewModel.setRate("9".repeat(11))
        runCurrent()

        assertEquals(false, viewModel.ready().canSave)
    }

    private fun TestScope.viewModel(repository: FakeSharedRepository): AddExpenseViewModel {
        val viewModel = AddExpenseViewModel(groupId = "g1", expenseId = null, repository = repository, categories = Category.seed())
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun AddExpenseViewModel.ready() = assertIs<AddExpenseUiState.Ready>(uiState.value)

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
