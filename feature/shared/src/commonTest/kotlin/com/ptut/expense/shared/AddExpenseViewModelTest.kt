package com.ptut.expense.shared

import kotlinx.coroutines.CompletableDeferred
import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.ExpenseChanged
import com.ptut.expense.domain.shared.GetLastGroupRateUseCase
import com.ptut.expense.domain.shared.LoadExpenseDraftUseCase
import com.ptut.expense.domain.shared.ObserveSharedSnapshotUseCase
import com.ptut.expense.domain.shared.QuickSplit
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.RemoveSharedExpenseUseCase
import com.ptut.expense.domain.shared.RestoreSharedExpenseUseCase
import com.ptut.expense.domain.shared.SaveSharedExpenseUseCase
import com.ptut.expense.domain.shared.SharedTextLimits
import com.ptut.expense.domain.shared.SplitMode
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.Language
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.inAppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddExpenseViewModelTest : ViewModelTest() {

    /** #6: the second tap lands while the first save is still writing. */
    @Test
    fun `two fast taps on Save add one expense`() = runTest {
        val repository = FakeSharedRepository()
        val viewModel = addExpenseViewModel(groupId = "g1", expenseId = null, repository = repository, categories = Category.seed())
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

    /** The rules refuse a longer text for good, and the expense would be taken back. */
    @Test
    fun `a description and note longer than the group takes are cut to its limits`() = runTest {
        val repository = FakeSharedRepository()
        val viewModel = addExpenseViewModel(groupId = "g1", expenseId = null, repository = repository, categories = Category.seed())
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.setAmount("240")
        viewModel.setDescription("d".repeat(SharedTextLimits.DESCRIPTION + 50))
        viewModel.setNote("n".repeat(SharedTextLimits.NOTE + 50))
        runCurrent()

        viewModel.save {}
        runCurrent()

        val saved = repository.expenses.single()
        assertEquals(SharedTextLimits.DESCRIPTION, saved.description.length)
        assertEquals(SharedTextLimits.NOTE, saved.note?.length)
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
        val viewModel = addExpenseViewModel(groupId = "g1", expenseId = dinner.id, repository = repository, categories = Category.seed())
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

    @Test
    fun `a new expense starts with You paying and an equal split`() = runTest {
        val ready = viewModel(FakeSharedRepository()).ready()

        assertEquals("me", ready.form.payerMemberId)
        assertEquals(SplitMode.Equally, ready.form.splitMode)
    }

    @Test
    fun `a new expense saved offers Undo and Undo removes it`() = runTest {
        val repository = FakeSharedRepository()
        val toasts = ToastMessages()
        val viewModel = addExpenseViewModel(groupId = "g1", expenseId = null, repository = repository, categories = Category.seed(), toasts = toasts)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.setAmount("240")
        viewModel.setDescription("Groceries")
        runCurrent()
        repository.gate.complete(Unit)
        viewModel.save {}
        runCurrent()

        val undo = assertNotNull(toasts.messages.first().action)
        assertEquals(AppText.copy.common.undo, undo.label)
        undo.onAction()

        assertEquals(listOf("expense-1"), repository.removedExpenses)
    }

    @Test
    fun `a failed save brings Save back and says so and the next tap saves`() = runTest {
        val repository = FakeSharedRepository()
        val toasts = ToastMessages()
        val viewModel = addExpenseViewModel(groupId = "g1", expenseId = null, repository = repository, categories = Category.seed(), toasts = toasts)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.setAmount("240")
        viewModel.setDescription("Groceries")
        runCurrent()
        repository.failNextExpenseWith = IllegalStateException("write failed")
        var closed = 0

        viewModel.save { closed++ }
        runCurrent()

        assertTrue(viewModel.ready().canSave)
        assertEquals("240", viewModel.ready().form.amountText)
        assertEquals(AppText.copy.shared.expense.didntSave, toasts.messages.first().text)
        assertEquals(0, closed)

        repository.gate.complete(Unit)
        viewModel.save { closed++ }
        runCurrent()

        assertEquals(1, repository.expenses.size)
        assertEquals(1, closed)
    }

    @Test
    fun `another phone's change keeps the form says so and the next save writes over it`() = runTest {
        val repository = FakeSharedRepository()
        val toasts = ToastMessages()
        val viewModel = viewModel(repository, toasts)
        viewModel.setAmount("240")
        runCurrent()
        repository.failNextExpenseWith = ExpenseChanged(latestVersionId = "v9", removed = false)

        viewModel.save()
        runCurrent()

        assertTrue(viewModel.ready().canSave)
        assertEquals(AppText.copy.shared.expense.changedElsewhere, toasts.messages.first().text)
        assertFalse(viewModel.isSaved.value)

        repository.gate.complete(Unit)
        viewModel.save()
        runCurrent()

        assertEquals("v9", repository.expenses.single().baseVersionId)
        assertTrue(viewModel.isSaved.value)
    }

    @Test
    fun `a saved expense says what it means for you and tells the screen to leave`() = runTest {
        val repository = FakeSharedRepository()
        val toasts = ToastMessages()
        val viewModel = viewModel(repository, toasts)
        viewModel.setAmount("240")
        viewModel.setDescription("Groceries")
        runCurrent()
        val meaning = viewModel.ready().meaning

        repository.gate.complete(Unit)
        viewModel.save()
        runCurrent()

        assertEquals("you are owed ฿120", meaning)
        assertEquals("Added Groceries · you are owed ฿120", toasts.messages.first().text)
        assertTrue(viewModel.isSaved.value)
    }

    @Test
    fun `a percent split that adds to a hundred pays each their part`() = runTest {
        val repository = FakeSharedRepository()
        val viewModel = viewModel(repository)
        viewModel.setAmount("100")
        viewModel.setSplitMode(SplitMode.Percent)
        viewModel.setWeight("me", "60")
        viewModel.setWeight("aye", "40")
        runCurrent()

        val split = viewModel.ready().split
        assertTrue(split.complete)
        assertEquals(listOf("฿60.00", "฿40.00"), split.rows.map { it.detail })

        repository.gate.complete(Unit)
        viewModel.save()
        runCurrent()
        assertEquals(mapOf("me" to 6_000L, "aye" to 4_000L), repository.expenses.single().weights)
    }

    @Test
    fun `shares split the amount in proportion`() = runTest {
        val repository = FakeSharedRepository()
        val viewModel = viewModel(repository)
        viewModel.setAmount("90")
        viewModel.setSplitMode(SplitMode.Shares)
        viewModel.setWeight("me", "2")
        viewModel.setWeight("aye", "1")
        runCurrent()

        assertEquals(listOf("฿60.00", "฿30.00"), viewModel.ready().split.rows.map { it.detail })

        repository.gate.complete(Unit)
        viewModel.save()
        runCurrent()
        assertEquals(mapOf("me" to 2L, "aye" to 1L), repository.expenses.single().weights)
    }

    @Test
    fun `owed in full by the other person puts the whole amount on you`() = runTest {
        val repository = FakeSharedRepository()
        val viewModel = viewModel(repository)
        viewModel.setAmount("240")
        viewModel.setQuickSplit("aye", QuickSplit.OwedInFull)
        runCurrent()

        val ready = viewModel.ready()
        assertEquals("aye", ready.form.payerMemberId)
        assertEquals(SplitMode.Shares, ready.form.splitMode)
        assertTrue(ready.quick.rows.single { it.selected }.owe)

        repository.gate.complete(Unit)
        viewModel.save()
        runCurrent()
        assertEquals(mapOf("me" to 1L), repository.expenses.single().weights)
    }

    @Test
    fun `a payer chip moves owed in full to the new payer`() = runTest {
        val viewModel = viewModel(FakeSharedRepository())
        viewModel.setAmount("240")
        viewModel.setQuickSplit("me", QuickSplit.OwedInFull)
        viewModel.setQuickPayer("aye")
        runCurrent()

        assertEquals(mapOf("me" to "1"), viewModel.ready().form.weightTexts)
    }

    @Test
    fun `give to puts what is left on the field edited last`() = runTest {
        val viewModel = viewModel(FakeSharedRepository())
        viewModel.setAmount("100")
        viewModel.setSplitMode(SplitMode.Exact)
        viewModel.setWeight("me", "30")
        viewModel.setWeight("aye", "30")
        runCurrent()
        assertEquals(AppText.copy.shared.expense.giveTo("Aye"), viewModel.ready().split.giveToLabel)

        viewModel.giveLeftToLastEdited()
        runCurrent()

        assertEquals("70.00", viewModel.ready().form.weightTexts["aye"])
        assertTrue(viewModel.ready().split.complete)
    }

    private fun TestScope.viewModel(repository: FakeSharedRepository, toasts: ToastMessages? = null): AddExpenseViewModel {
        val viewModel = addExpenseViewModel(groupId = "g1", expenseId = null, repository = repository, categories = Category.seed(), toasts = toasts)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun AddExpenseViewModel.ready() = assertIs<AddExpenseUiState.Ready>(uiState.value)

    @Test
    fun `the wheel's pick opens as the payer and a name no longer in the group falls back to you`() = runTest {
        val picked = addExpenseViewModel(groupId = "g1", expenseId = null, repository = FakeSharedRepository(), categories = Category.seed(), payer = "aye")
        val gone = addExpenseViewModel(groupId = "g1", expenseId = null, repository = FakeSharedRepository(), categories = Category.seed(), payer = "zed")
        backgroundScope.launch { picked.uiState.collect {} }
        backgroundScope.launch { gone.uiState.collect {} }
        runCurrent()

        assertEquals("aye", picked.ready().form.payerMemberId)
        assertEquals("me", gone.ready().form.payerMemberId)
    }

    /** The categories go in as the app hands them over: installed names already in the app's language. */
    private fun TestScope.titleSavedWithNoDescription(stored: List<Category>): String {
        val repository = FakeSharedRepository()
        val categories = stored.map(Category::inAppLanguage)
        val viewModel = addExpenseViewModel(groupId = "g1", expenseId = null, repository = repository, categories = categories)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.setAmount("240")
        runCurrent()

        viewModel.save {}
        repository.gate.complete(Unit)
        runCurrent()

        return repository.expenses.single().description
    }

    @Test
    fun `two fast taps on Remove remove the expense once and close once`() = runTest {
        val repository = FakeSharedRepository().apply { removeGate = CompletableDeferred() }
        val viewModel = addExpenseViewModel(groupId = "g1", expenseId = "e1", repository = repository, categories = Category.seed())
        var closed = 0

        viewModel.remove { closed++ }
        viewModel.remove { closed++ }
        runCurrent()
        repository.removeGate?.complete(Unit)
        runCurrent()

        assertEquals(listOf("e1"), repository.removedExpenses)
        assertEquals(1, closed)
    }

    @Test
    fun `a failed remove says so and keeps the screen and the next tap removes`() = runTest {
        val repository = FakeSharedRepository().apply { failNextRemove = true }
        val toasts = ToastMessages()
        val viewModel = addExpenseViewModel(groupId = "g1", expenseId = "e1", repository = repository, categories = Category.seed(), toasts = toasts)
        var closed = 0

        viewModel.remove { closed++ }
        runCurrent()

        assertEquals(AppText.copy.common.notDone, toasts.messages.first().text)
        assertEquals(0, closed)

        viewModel.remove { closed++ }
        runCurrent()

        assertEquals(listOf("e1"), repository.removedExpenses)
        assertEquals(1, closed)
    }
}

/** Add expense as Koin builds it, its use cases over [repository]. */
private fun addExpenseViewModel(groupId: String, expenseId: String?, repository: FakeSharedRepository, categories: List<Category>, toasts: ToastMessages? = null, payer: String? = null) =
    AddExpenseViewModel(
        groupId = groupId,
        expenseId = expenseId,
        categories = categories,
        observeSnapshot = ObserveSharedSnapshotUseCase(repository),
        loadDraft = LoadExpenseDraftUseCase(repository),
        getLastRate = GetLastGroupRateUseCase(repository),
        saveExpense = SaveSharedExpenseUseCase(repository),
        removeExpense = RemoveSharedExpenseUseCase(repository),
        restoreExpense = RestoreSharedExpenseUseCase(repository),
        toasts = toasts,
        prefillPayer = payer,
    )
