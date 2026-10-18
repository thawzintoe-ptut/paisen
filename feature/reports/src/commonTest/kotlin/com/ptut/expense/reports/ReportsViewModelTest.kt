package com.ptut.expense.reports

import app.cash.turbine.test
import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReportPeriod
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ReportsViewModelTest : ViewModelTest() {

    @Test
    fun totalsTheChosenPeriodAndSaysHowItComparesWithTheOneBefore() = runTest {
        val viewModel = viewModel(
            listOf(
                expense(day = 12, satang = 40_000, categoryId = "food"),
                expense(day = 5, satang = 50_000, categoryId = "food"),
            ),
        )

        viewModel.uiState.test {
            awaitItem()

            val state = awaitItem() as ReportsUiState.Success
            assertEquals("Spent Mon 10 – Sun 16", state.periodLabel)
            assertEquals(Satang(40_000), state.total)
            assertEquals("20% less than last week", state.delta?.text)
        }
    }

    @Test
    fun offersNoComparisonWhenThereIsNoEarlierPeriodToCompareWith() = runTest {
        val viewModel = viewModel(listOf(expense(day = 12, satang = 40_000, categoryId = "food")))

        viewModel.uiState.test {
            awaitItem()
            assertEquals(null, (awaitItem() as ReportsUiState.Success).delta)
        }
    }

    @Test
    fun switchingToTheMonthRetotalsWithoutLosingTheEarlierRows() = runTest {
        val viewModel = viewModel(
            listOf(
                expense(day = 12, satang = 40_000, categoryId = "food"),
                expense(day = 5, satang = 50_000, categoryId = "transport"),
            ),
        )

        viewModel.uiState.test {
            awaitItem()
            awaitItem()

            viewModel.show(ReportPeriod.Month)

            val state = awaitItem() as ReportsUiState.Success
            assertEquals("Spent 1 – 14 Aug", state.periodLabel)
            assertEquals(Satang(90_000), state.total)
            assertEquals(listOf("Transport", "Food & drink"), state.categories.map { it.name })
        }
    }

    /** The empty state cannot be read off the expense total, or a paid-in week claims nothing. */
    @Test
    fun aPeriodOfNothingButIncomeStillCountsAsRecorded() = runTest {
        val viewModel = viewModel(listOf(income(day = 12, satang = 450_000)))

        viewModel.uiState.test {
            awaitItem()

            val state = awaitItem() as ReportsUiState.Success
            assertEquals(Satang.Zero, state.total)
            assertTrue(state.hasTransactions, "an income-only week reads as nothing recorded")
        }
    }

    /**
     * A zero day is a day nothing was recorded on, which is not the claim that nothing was spent.
     * The bar keeps its stub height in the view for the same reason (FR-20).
     */
    @Test
    fun aDayWithNoSpendReadsAsADashAndTheWeekStillHoldsSevenBars() = runTest {
        val viewModel = viewModel(listOf(expense(day = 12, satang = 40_000, categoryId = "food")))

        viewModel.uiState.test {
            awaitItem()

            val days = (awaitItem() as ReportsUiState.Success).bars
            assertEquals(7, days.size)
            assertEquals(1, days.count { it.amount != "\u2013" }, days.map { it.amount }.toString())
        }
    }

    @Test
    fun aPeriodWithNoRowsAtAllIsEmpty() = runTest {
        val viewModel = viewModel(emptyList())

        viewModel.uiState.test {
            awaitItem()
            assertFalse((awaitItem() as ReportsUiState.Success).hasTransactions)
        }
    }

    /** p34: the parent stays the main read, and its split is listed under it at lower weight. */
    @Test
    fun aParentFiledUnderSubCategoriesListsItsSplitUnderneath() = runTest {
        val viewModel = viewModel(
            transactions = listOf(
                expense(day = 12, satang = 30_000, categoryId = "groceries").copy(subCategoryId = "supermarket"),
                expense(day = 13, satang = 10_000, categoryId = "groceries"),
            ),
            subCategories = flowOf(listOf(SubCategory("supermarket", "groceries", "Supermarket"))),
        )

        viewModel.uiState.test {
            awaitItem()
            val groceries = (awaitItem() as ReportsUiState.Success).categories.single()
            assertEquals("฿400.00", groceries.amount)
            assertEquals(
                listOf(SubCategorySpendUiModel("Supermarket", "฿300.00"), SubCategorySpendUiModel("Unsorted", "฿100.00")),
                groceries.subCategories,
            )
        }
    }

    /** #115: the tab keeps its holder, so a rename has to reach it without a rebuild. */
    @Test
    fun aRenameReachesTheBreakdownWithoutRebuildingTheScreen() = runTest {
        val categories = MutableStateFlow(Category.seed())
        val viewModel = viewModel(listOf(expense(day = 12, satang = 40_000, categoryId = "food")), categories)

        viewModel.uiState.test {
            awaitItem()
            assertEquals(listOf("Food & drink"), (awaitItem() as ReportsUiState.Success).categories.map { it.name })

            categories.value = Category.seed().map { if (it.id == "food") it.copy(name = "Eating out") else it }

            assertEquals(listOf("Eating out"), (awaitItem() as ReportsUiState.Success).categories.map { it.name })
        }
    }

    /** #115: spend in a category added after the screen was built still lands in the breakdown. */
    @Test
    fun spendInANewCategoryKeepsTheBreakdownEqualToTheTotal() = runTest {
        val categories = MutableStateFlow(Category.seed())
        val viewModel = viewModel(
            listOf(
                expense(day = 12, satang = 40_000, categoryId = "food"),
                expense(day = 13, satang = 10_000, categoryId = "pets"),
            ),
            categories,
        )

        viewModel.uiState.test {
            awaitItem()
            val before = awaitItem() as ReportsUiState.Success
            assertEquals(listOf("Food & drink", "Unknown category"), before.categories.map { it.name })

            categories.value = Category.seed() + Category(id = "pets", name = "Pets", icon = "more_horiz")

            val after = awaitItem() as ReportsUiState.Success
            assertEquals(listOf("Food & drink", "Pets"), after.categories.map { it.name })
            assertEquals(100, after.categories.sumOf { it.share })
        }
    }

    private fun viewModel(
        transactions: List<Transaction>,
        categories: Flow<List<Category>> = flowOf(Category.seed()),
        subCategories: Flow<List<SubCategory>> = flowOf(emptyList()),
    ) = ReportsViewModel(
        ledger = FakeLedger(transactions),
        clock = object : Clock {
            override fun now(): Instant = at(day = 14, hour = 12)
        },
        categoryUpdates = categories,
        subCategoryUpdates = subCategories,
    )

    /** Reports only reads recent history; the rest of the seam is not its business. */
    private class FakeLedger(transactions: List<Transaction>) : LedgerRepository {
        override val todaysTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(emptyList()))
        override val recentTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(transactions))

        override val allTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(transactions))
        override val sinceLastImportMillis: StateFlow<Long?> = MutableStateFlow(null)
        override val importRuns: StateFlow<List<ImportRun>> = MutableStateFlow(emptyList())

        override val queuedSlips: StateFlow<List<QueuedSlip>> = MutableStateFlow(emptyList())

        /** Reports never deletes, so a call here is a screen reaching past its job. */

        override suspend fun delete(transactionId: String): Unit = error("Reports never deletes")

        override suspend fun restore(transaction: Transaction): Unit = error("Reports never deletes")

        override suspend fun applyEdit(transactionId: String, edit: TransactionEdit): Unit =
            error("Reports never edits")

        override suspend fun record(draft: TransactionDraft) = error("Reports never writes")
    }

    private fun expense(day: Int, satang: Long, categoryId: String) = Transaction(
        id = "$day$satang",
        occurredAtEpochMillis = at(day = day, hour = 12).toEpochMilliseconds(),
        amount = Satang(satang),
        direction = Direction.Expense,
        categoryId = categoryId,
        counterparty = "Somewhere",
    )

    private fun income(day: Int, satang: Long) = Transaction(
        id = "in$day$satang",
        occurredAtEpochMillis = at(day = day, hour = 12).toEpochMilliseconds(),
        amount = Satang(satang),
        direction = Direction.Income,
        categoryId = null,
        counterparty = "Payroll",
    )

    private fun at(day: Int, hour: Int): Instant =
        LocalDateTime(2026, 8, day, hour, 0).toInstant(BangkokTime)
}
