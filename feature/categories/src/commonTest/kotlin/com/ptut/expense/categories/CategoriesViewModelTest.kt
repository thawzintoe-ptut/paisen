package com.ptut.expense.categories

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** US-9: a settings list that doubles as a read of where the money went. */
class CategoriesViewModelTest {

    /** 14 Aug 2026, 18:42 Bangkok. The month starts on the 1st, so the 3rd is inside it. */
    private val now = Instant.fromEpochMilliseconds(1_786_800_000_000)

    private val food = Category("food", "Food & drink", "restaurant", sortOrder = 0)
    private val groceries = Category("groceries", "Groceries", "local_grocery_store", sortOrder = 1)

    @BeforeTest
    fun pinTheDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun releaseTheDispatcher() = Dispatchers.resetMain()

    @Test
    fun `a row carries this month's spend and its sub-categories as chips`() = runTest {
        val model = viewModel(
            categories = listOf(Category("food", "Food & drink", "restaurant")),
            subs = listOf(
                SubCategory("eat", "food", "Eating out"),
                SubCategory("coffee", "food", "Coffee"),
            ),
            transactions = listOf(spend("food", 198_000, daysAgo = 1), spend("food", 64_000, daysAgo = 2)),
        )
        runCurrent()

        val row = model.uiState.value.rows.single()
        assertEquals("฿2,620", row.spentThisMonth)
        assertEquals("2 sub-categories · ฿2,620 this month", row.meta)
        assertEquals(listOf("Eating out", "Coffee"), row.subCategories)
    }

    /**
     * The design writes the count in words, and a category nobody has split says so rather than
     * reading "0 sub-categories" — a zero there looks like a count that failed.
     */
    @Test
    fun `the second line counts in words and says when there is nothing to count`() = runTest {
        val model = viewModel(
            categories = listOf(
                Category("food", "Food & drink", "restaurant"),
                Category("bills", "Bills", "receipt_long"),
            ),
            subs = listOf(SubCategory("eat", "food", "Eating out")),
        )
        runCurrent()

        val rows = model.uiState.value.rows.associateBy { it.id }
        assertEquals("1 sub-category · ฿0 this month", rows.getValue("food").meta)
        assertEquals("No sub-categories yet · ฿0 this month", rows.getValue("bills").meta)
    }

    @Test
    fun `spending before this month is not counted`() = runTest {
        val model = viewModel(
            categories = listOf(Category("food", "Food & drink", "restaurant")),
            transactions = listOf(spend("food", 500_000, daysAgo = 60)),
        )
        runCurrent()

        assertEquals("฿0", model.uiState.value.rows.single().spentThisMonth)
    }

    @Test
    fun `money in is not counted as money out`() = runTest {
        val model = viewModel(
            categories = listOf(Category("income", "Income", "payments")),
            transactions = listOf(earn("income", 3_200_000, daysAgo = 1)),
        )
        runCurrent()

        assertEquals("฿0", model.uiState.value.rows.single().spentThisMonth)
    }

    @Test
    fun `a renamed category reaches the screen without touching a transaction`() = runTest {
        val categories = FakeCategories(listOf(Category("food", "Food & drink", "restaurant")))
        val model = watching(CategoriesViewModel(ledger(listOf(spend("food", 100_00, daysAgo = 1))), categories, FixedClock(now)))
        runCurrent()

        categories.save(Category("food", "Eating", "restaurant"))
        runCurrent()

        val row = model.uiState.value.rows.single()
        assertEquals("Eating", row.name)
        // The id never moved, so the spending is still filed under it.
        assertEquals("฿100", row.spentThisMonth)
    }

    @Test
    fun `an unanswered ledger is not drawn as a month of zeros`() = runTest {
        val model = watching(
            CategoriesViewModel(
                UnansweredLedger(),
                FakeCategories(listOf(Category("food", "Food & drink", "restaurant"))),
                FixedClock(now),
            ),
        )
        runCurrent()

        assertFalse(model.uiState.value.loaded)
        assertTrue(model.uiState.value.rows.isEmpty())
    }

    /**
     * The first move is the one that could quietly do nothing: every seeded category can share a
     * sortOrder, and swapping two zeroes changes no order at all.
     */
    @Test
    fun `moving a row up writes the whole order and not a swap of two`() = runTest {
        val flat = listOf(
            Category("food", "Food & drink", "restaurant"),
            Category("groceries", "Groceries", "local_grocery_store"),
            Category("transport", "Transport", "directions_bus"),
        )
        val model = viewModel(flat)
        runCurrent()
        assertEquals(listOf("food", "groceries", "transport"), model.uiState.value.rows.map { it.id })

        model.move("transport", -1)
        runCurrent()

        assertEquals(listOf("food", "transport", "groceries"), model.uiState.value.rows.map { it.id })
    }

    @Test
    fun `a row at the end of the list has nowhere further to go`() = runTest {
        val model = viewModel(listOf(food, groceries))
        runCurrent()

        val rows = model.uiState.value.rows
        assertFalse(rows.first().canMoveUp)
        assertTrue(rows.first().canMoveDown)
        assertTrue(rows.last().canMoveUp)
        assertFalse(rows.last().canMoveDown)
    }

    @Test
    fun `a move past either end is refused rather than clamped`() = runTest {
        val model = viewModel(listOf(food, groceries))
        runCurrent()

        model.move("food", -1)
        runCurrent()

        assertEquals(listOf("food", "groceries"), model.uiState.value.rows.map { it.id })
    }

    /** A new one landing on top would move every category the reader already knows where to find. */
    @Test
    fun `a new category gets an id and nothing is stored until Edit category saves it`() = runTest {
        val model = viewModel(listOf(food, groceries))
        runCurrent()

        model.newCategory()
        runCurrent()

        assertTrue(model.justCreated.value != null)
        assertEquals(2, model.uiState.value.rows.size)
    }

    @Test
    fun `a row carries the colour its category wears`() = runTest {
        val model = viewModel(listOf(food.copy(color = "rose"), groceries))
        runCurrent()

        assertEquals("rose", model.uiState.value.rows.first { it.id == "food" }.color)
    }

    private fun TestScope.viewModel(
        categories: List<Category>,
        subs: List<SubCategory> = emptyList(),
        transactions: List<Transaction> = emptyList(),
    ) = watching(CategoriesViewModel(ledger(transactions), FakeCategories(categories, subs), FixedClock(now)))

    private fun TestScope.watching(model: CategoriesViewModel): CategoriesViewModel {
        backgroundScope.launch { model.uiState.collect {} }
        return model
    }

    private fun ledger(transactions: List<Transaction>) = StaticLedger(transactions)

    private fun spend(categoryId: String, minor: Long, daysAgo: Int, subCategoryId: String? = null) =
        row(categoryId, minor, daysAgo, Direction.Expense, subCategoryId)

    private fun earn(categoryId: String, minor: Long, daysAgo: Int) =
        row(categoryId, minor, daysAgo, Direction.Income, null)

    private fun row(
        categoryId: String,
        minor: Long,
        daysAgo: Int,
        direction: Direction,
        subCategoryId: String?,
    ) = Transaction(
        id = "$categoryId-$minor-$daysAgo",
        occurredAtEpochMillis = now.toEpochMilliseconds() - daysAgo * 86_400_000L,
        amount = Satang.ofMinorUnits(minor),
        direction = direction,
        categoryId = categoryId,
        subCategoryId = subCategoryId,
        counterparty = null,
    )

    private class FixedClock(private val at: Instant) : Clock {
        override fun now(): Instant = at
    }
}

internal class StaticLedger(transactions: List<Transaction>) : LedgerRepository {
    override val todaysTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(transactions))
    override val recentTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(transactions))
    override val allTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(transactions))
    override val sinceLastImportMillis: StateFlow<Long?> = MutableStateFlow(null)
    override val importRuns: StateFlow<List<ImportRun>> = MutableStateFlow(emptyList())
    override val queuedSlips: StateFlow<List<QueuedSlip>> = MutableStateFlow(emptyList())

    override suspend fun record(draft: TransactionDraft) = Unit
    override suspend fun delete(transactionId: String): Unit = error("Categories never delete a row")
    override suspend fun restore(transaction: Transaction): Unit = error("Categories never delete a row")
    override suspend fun applyEdit(transactionId: String, edit: TransactionEdit): Unit =
        error("Categories never edit a row")
}

internal class UnansweredLedger : LedgerRepository {
    override val todaysTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Unanswered)
    override val recentTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Unanswered)
    override val allTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Unanswered)
    override val sinceLastImportMillis: StateFlow<Long?> = MutableStateFlow(null)
    override val importRuns: StateFlow<List<ImportRun>> = MutableStateFlow(emptyList())
    override val queuedSlips: StateFlow<List<QueuedSlip>> = MutableStateFlow(emptyList())

    override suspend fun record(draft: TransactionDraft) = Unit
    override suspend fun delete(transactionId: String): Unit = error("Categories never delete a row")
    override suspend fun restore(transaction: Transaction): Unit = error("Categories never delete a row")
    override suspend fun applyEdit(transactionId: String, edit: TransactionEdit): Unit =
        error("Categories never edit a row")
}
