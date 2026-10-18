package com.ptut.expense.add

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val Food = Category("food", "Food & drink", "restaurant")
private val Groceries = Category.seed().first { it.id == "groceries" }
private val Transport = Category.seed().first { it.id == "transport" }
private val Supermarket = SubCategory("supermarket", "groceries", "Supermarket")
private val Market = SubCategory("market", "groceries", "Market")
private val Bts = SubCategory("bts", "transport", "BTS")

class AddViewModelTest : ViewModelTest() {

    /**
     * The gap the direction column closes. The split used to be two lists compiled into
     * `Category.kt`, so a category the reader made was in neither and never reached this sheet —
     * it could be named on History and still be impossible to file anything under.
     */
    @Test
    fun `a category the reader made is offered under its own kind`() = runTest {
        val gym = Category("gym", "Gym", "more_horiz")
        val tips = Category("tips", "Tips", "payments", Direction.Income)
        val held = Category.seed() + gym + tips

        val viewModel = AddViewModel(
            ledger = RecordingLedger(),
            toasts = ToastMessages(),
            categories = { held.forDirection(it) },
        )
        runCurrent()

        assertTrue(gym in viewModel.uiState.value.categories)
        assertFalse(tips in viewModel.uiState.value.categories)

        viewModel.setKind(Direction.Income)
        runCurrent()

        assertTrue(tips in viewModel.uiState.value.categories)
        assertFalse(gym in viewModel.uiState.value.categories)
    }

    /**
     * Income and spending share no category, so a choice made under one kind cannot survive the
     * switch — keeping it would file a salary under Groceries with nobody having chosen that.
     */
    @Test
    fun `switching to income swaps the chips and drops the category already chosen`() = runTest {
        val viewModel = AddViewModel(ledger = RecordingLedger(), toasts = ToastMessages())
        viewModel.choose(Category.spending.first())
        runCurrent()

        viewModel.setKind(Direction.Income)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf("Income"), state.categories.map { it.name })
        assertNull(state.category)
        assertFalse(state.canSave, "no category is chosen yet, so the key stays dark")
    }

    @Test
    fun aSaveWritesTheTypedAmountAgainstTheChosenCategory() = runTest {
        val ledger = RecordingLedger()
        val viewModel = AddViewModel(ledger = ledger, toasts = ToastMessages())

        viewModel.type(entryOf("485"))
        viewModel.choose(Food)
        viewModel.save()
        runCurrent()

        assertEquals(
            TransactionDraft(
                amount = Satang(48_500),
                direction = Direction.Expense,
                categoryId = "food",
            ),
            ledger.recorded.single(),
        )
    }

    /** FR-14 and plan §G item 6: no category, no row — and nothing claiming one was written. */
    @Test
    fun aSaveWithNoCategoryWritesNothingAndSaysNothing() = runTest {
        val ledger = RecordingLedger()
        val toasts = ToastMessages()
        val said = collect(toasts)
        val viewModel = AddViewModel(ledger = ledger, toasts = toasts)

        viewModel.type(entryOf("485"))
        viewModel.save()
        runCurrent()

        assertTrue(ledger.recorded.isEmpty(), "a row was written without a category")
        assertTrue(said.isEmpty(), "the app claimed a save it never made")
        assertFalse(viewModel.uiState.value.saved, "the sheet would have closed on nothing")
    }

    @Test
    fun `no sub-category is offered before a category is picked`() = runTest {
        val viewModel = withSubCategories()

        assertEquals(emptyList(), viewModel.uiState.value.subCategories)
    }

    @Test
    fun `picking a category offers its own sub-categories`() = runTest {
        val viewModel = withSubCategories()

        viewModel.choose(Groceries)

        assertEquals(listOf(Supermarket, Market), viewModel.uiState.value.subCategories)
    }

    @Test
    fun `a category with no sub-categories offers none`() = runTest {
        val viewModel = withSubCategories()

        viewModel.choose(Food)

        assertEquals(emptyList(), viewModel.uiState.value.subCategories)
    }

    @Test
    fun `picking another category clears the sub-category`() = runTest {
        val viewModel = withSubCategories()
        viewModel.choose(Groceries)
        viewModel.chooseSubCategory(Supermarket)

        viewModel.choose(Transport)

        val state = viewModel.uiState.value
        assertNull(state.subCategory)
        assertEquals(listOf(Bts), state.subCategories)
    }

    @Test
    fun `switching to income clears the sub-category`() = runTest {
        val viewModel = withSubCategories()
        viewModel.choose(Groceries)
        viewModel.chooseSubCategory(Supermarket)

        viewModel.setKind(Direction.Income)

        val state = viewModel.uiState.value
        assertNull(state.subCategory)
        assertEquals(emptyList(), state.subCategories)
    }

    @Test
    fun `tapping the chosen sub-category again clears it`() = runTest {
        val viewModel = withSubCategories()
        viewModel.choose(Groceries)

        viewModel.chooseSubCategory(Supermarket)
        viewModel.chooseSubCategory(Supermarket)

        assertNull(viewModel.uiState.value.subCategory)
    }

    @Test
    fun `a save files the row under the chosen sub-category`() = runTest {
        val ledger = RecordingLedger()
        val viewModel = withSubCategories(ledger)

        viewModel.type(entryOf("485"))
        viewModel.choose(Groceries)
        viewModel.chooseSubCategory(Supermarket)
        viewModel.save()
        runCurrent()

        assertEquals("supermarket", ledger.recorded.single().subCategoryId)
    }

    private fun withSubCategories(ledger: RecordingLedger = RecordingLedger()) = AddViewModel(
        ledger = ledger,
        toasts = ToastMessages(),
        subCategories = { listOf(Supermarket, Market, Bts) },
    )

    @Test
    fun aSaveWithNoAmountWritesNothing() = runTest {
        val ledger = RecordingLedger()
        val viewModel = AddViewModel(ledger = ledger, toasts = ToastMessages())

        viewModel.choose(Food)
        viewModel.save()
        runCurrent()

        assertTrue(ledger.recorded.isEmpty())
    }

    /** `Satang.format()` pads the satang; the design's line is `Saved ฿485 · Food & drink`. */
    @Test
    fun theToastNamesWholeBahtAndTheCategory() = runTest {
        val toasts = ToastMessages()
        val said = collect(toasts)
        val viewModel = AddViewModel(ledger = RecordingLedger(), toasts = toasts)

        viewModel.type(entryOf("485"))
        viewModel.choose(Food)
        viewModel.save()
        runCurrent()

        assertEquals(ToastMessage("Saved ฿485 · Food & drink"), said.single())
    }

    @Test
    fun incomeIsWrittenAsIncomeRatherThanAsANegativeExpense() = runTest {
        val ledger = RecordingLedger()
        val viewModel = AddViewModel(ledger = ledger, toasts = ToastMessages())

        viewModel.setKind(Direction.Income)
        viewModel.type(entryOf("4500"))
        viewModel.choose(Food)
        viewModel.save()
        runCurrent()

        assertEquals(Direction.Income, ledger.recorded.single().direction)
    }

    /** Two taps on one row would otherwise be two rows: the write is not instant. */
    @Test
    fun aSecondTapWhileTheFirstSaveIsStillWritingAddsNothing() = runTest {
        val ledger = RecordingLedger()
        val viewModel = AddViewModel(ledger = ledger, toasts = ToastMessages())

        viewModel.type(entryOf("485"))
        viewModel.choose(Food)
        viewModel.save()
        viewModel.save()
        runCurrent()

        assertEquals(1, ledger.recorded.size)
    }

    @Test
    fun tappingTheChosenCategoryAgainClearsItSoAMisTapIsUndoneWhereItWasMade() = runTest {
        val viewModel = AddViewModel(ledger = RecordingLedger(), toasts = ToastMessages())

        viewModel.type(entryOf("485"))
        viewModel.choose(Food)
        viewModel.choose(Food)

        assertEquals(null, viewModel.uiState.value.category)
        assertFalse(viewModel.uiState.value.canSave)
    }

    /** The design reopens the sheet cleared, and this holder outlives the sheet. */
    @Test
    fun resetClearsTheAmountTheCategoryAndTheKind() = runTest {
        val viewModel = AddViewModel(ledger = RecordingLedger(), toasts = ToastMessages())

        viewModel.setKind(Direction.Income)
        viewModel.type(entryOf("485"))
        viewModel.choose(Food)
        viewModel.reset()

        assertEquals(AddUiState(), viewModel.uiState.value)
    }

    @Test
    fun theSaveKeySaysWhyItIsDarkOnceAnAmountIsTypedWithNoCategory() = runTest {
        val viewModel = AddViewModel(ledger = RecordingLedger(), toasts = ToastMessages())

        assertEquals("Added by hand · no slip for this", viewModel.uiState.value.hint)

        viewModel.type(entryOf("485"))

        assertEquals("Pick a category to save this", viewModel.uiState.value.hint)
    }

    private fun entryOf(typed: String): AmountEntry =
        typed.fold(AmountEntry()) { entry, key -> if (key == '.') entry.decimal() else entry.digit(key) }

    /** Everything the toast seam carried during the test, in order. */
    private fun TestScope.collect(toasts: ToastMessages): List<ToastMessage> =
        mutableListOf<ToastMessage>().also { seen -> backgroundScope.launch { toasts.messages.toList(seen) } }
}

/** A ledger with nothing in it that remembers what it was asked to write. */
private class RecordingLedger : LedgerRepository {
    val recorded = mutableListOf<TransactionDraft>()

    override val todaysTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(emptyList()))
    override val recentTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(emptyList()))

    override val allTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(emptyList()))
    override val sinceLastImportMillis: StateFlow<Long?> = MutableStateFlow(null)
    override val importRuns: StateFlow<List<ImportRun>> = MutableStateFlow(emptyList())

    override val queuedSlips: StateFlow<List<QueuedSlip>> = MutableStateFlow(emptyList())

    /** The add sheet never deletes, so a call here is a screen reaching past its job. */

    override suspend fun delete(transactionId: String): Unit = error("The add sheet never deletes")

    override suspend fun restore(transaction: Transaction): Unit = error("The add sheet never deletes")

    override suspend fun applyEdit(transactionId: String, edit: TransactionEdit): Unit =
        error("The add sheet never edits")

    override suspend fun record(draft: TransactionDraft) {
        recorded += draft
    }
}
