package com.ptut.expense.home

import app.cash.turbine.test
import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ImportFix
import com.ptut.expense.domain.ImportOutcome
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.MetaParts
import com.ptut.expense.domain.Provenance
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.TransactionSource
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
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

class HomeViewModelTest : ViewModelTest() {

    @Test
    fun showsTodaysNetAndTheDaysTransactions() = runTest {
        val viewModel = viewModel(
            transactions = listOf(
                transaction(Satang(450_000), Direction.Income),
                transaction(Satang(12_875), Direction.Expense),
            ),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(Satang(437_125), state.summary.net)
            assertEquals(2, state.transactions.size)
        }
    }

    /**
     * A slip confirmed today from 12 Aug belongs to 12 Aug's totals, so it is shown and not
     * counted. Before this, Home said nothing at all about a save the reader had just made.
     */
    @Test
    fun aSlipSavedTodayFromAnEarlierDayIsShownWithItsDayButNotCounted() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            saved = listOf(saved("lawson", occurred = at(2026, 8, 12, 19, 20), filed = at(2026, 8, 14, 11, 0))),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(listOf("lawson"), state.savedToday.map { it.id })
            assertEquals("Wed 12 Aug · Food & drink", state.savedToday.single().meta)
            assertEquals(Satang.Zero, state.summary.expense)
        }
    }

    @Test
    fun savedTodayShowsTheNewestThreeAndCountsTheRest() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            saved = (1..5).map { saved("s$it", occurred = at(2026, 8, 1, 9, 0), filed = at(2026, 8, 14, 9, it)) },
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(listOf("s5", "s4", "s3"), state.savedToday.map { it.id })
            assertEquals(2, state.savedTodayMore)
        }
    }

    @Test
    fun flagsTheTotalWhenTheImporterHasNotRunForDays() = runTest {
        val fourDays = 4L * 24 * 60 * 60 * 1000
        val viewModel = viewModel(transactions = emptyList(), sinceLastImportMillis = fourDays)

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(ImportStatusUiModel.Broken("4 days"), state.importStatus)
        }
    }

    @Test
    fun aFreshInstallSaysNoImportHasRunRatherThanNamingASpan() = runTest {
        val viewModel = viewModel(transactions = emptyList(), sinceLastImportMillis = null)

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(ImportStatusUiModel.NeverRun, state.importStatus)
        }
    }

    @Test
    fun offersTheFixThatMatchesWhichWayTheImporterFailed() = runTest {
        val fourDays = 4L * 24 * 60 * 60 * 1000

        assertEquals(ImportFix.GrantPhotoAccess, importFixFor(sinceLastImportMillis = null))
        assertEquals(ImportFix.ResumeWatching, importFixFor(sinceLastImportMillis = fourDays))
    }

    @Test
    fun offersNothingWhileAQuietStretchIsStillPlausiblyHealthy() = runTest {
        val twoDays = 2L * 24 * 60 * 60 * 1000

        assertNull(importFixFor(sinceLastImportMillis = 0L))
        assertNull(importFixFor(sinceLastImportMillis = twoDays))
    }

    @Test
    fun aScanThatFailedWarnsAndOffersTheFixWhileTheLastImportIsStillRecent() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            sinceLastImportMillis = FIVE_MINUTES,
            runs = listOf(scan(ImportOutcome.Running), scan(ImportOutcome.Failure), scan(ImportOutcome.Success)),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(ImportStatusUiModel.Failing, state.importStatus)
            assertEquals(ImportFix.AllowAllPhotos, state.importFix)
        }
    }

    @Test
    fun aSuccessAfterAFailedScanClearsTheWarning() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            sinceLastImportMillis = FIVE_MINUTES,
            runs = listOf(scan(ImportOutcome.Success), scan(ImportOutcome.Failure)),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())

            val state = awaitItem() as HomeUiState.Success
            assertEquals(ImportStatusUiModel.Fresh("5 min"), state.importStatus)
            assertNull(state.importFix)
        }
    }

    private fun scan(outcome: ImportOutcome) = ImportRun(
        id = outcome.name,
        startedAtEpochMillis = 0L,
        finishedAtEpochMillis = null,
        outcome = outcome,
        slipsFound = 0,
        failureReason = null,
    )

    private suspend fun importFixFor(sinceLastImportMillis: Long?): ImportFix? {
        var fix: ImportFix? = null
        viewModel(transactions = emptyList(), sinceLastImportMillis = sinceLastImportMillis)
            .uiState.test {
                awaitItem()
                fix = (awaitItem() as HomeUiState.Success).importFix
            }
        return fix
    }

    @Test
    fun aSlipNameTheReaderCouldOnlyMakeNoiseOfShowsAsUnknown() = runTest {
        val viewModel = viewModel(
            transactions = listOf(
                Transaction(
                    id = "1",
                    occurredAtEpochMillis = LocalDateTime(2026, 8, 14, 12, 41)
                        .toInstant(BangkokTime).toEpochMilliseconds(),
                    amount = Satang(1_000),
                    direction = Direction.Expense,
                    categoryId = "groceries",
                    counterparty = "msus:quus:óiÜangönms",
                    source = TransactionSource.Scan,
                ),
            ),
        )

        viewModel.uiState.test {
            awaitItem()
            assertEquals("Unknown shop", (awaitItem() as HomeUiState.Success).transactions.single().merchant)
        }
    }

    @Test
    fun describesEachRowWellEnoughForTheListToDrawItWithoutTheDomain() = runTest {
        val viewModel = viewModel(
            transactions = listOf(
                Transaction(
                    id = "1",
                    occurredAtEpochMillis = LocalDateTime(2026, 8, 14, 18, 30)
                        .toInstant(BangkokTime).toEpochMilliseconds(),
                    amount = Satang(64_250),
                    direction = Direction.Expense,
                    categoryId = "groceries",
                    counterparty = "Foodland Rama IV",
                    source = TransactionSource.Scan,
                ),
            ),
        )

        viewModel.uiState.test {
            awaitItem()

            val row = (awaitItem() as HomeUiState.Success).transactions.single()
            assertEquals("Foodland Rama IV", row.merchant)
            assertEquals("Groceries · 18:30 · auto", row.meta)
            assertEquals(MetaParts(category = "Groceries", time = "18:30", provenance = Provenance.Auto), row.parts)
            assertEquals("฿642.50", row.amount)
            assertEquals("local_grocery_store", row.icon)
            assertEquals(Direction.Expense, row.direction)
        }
    }

    @Test
    fun aRowFiledUnderASubCategoryNamesThePairOnTheListAndInSavedToday() = runTest {
        val grocery = saved("bigc", occurred = at(2026, 8, 14, 18, 30), filed = at(2026, 8, 14, 18, 31))
            .copy(categoryId = "groceries", subCategoryId = "supermarket")
        val earlier = saved("lawson", occurred = at(2026, 8, 12, 19, 20), filed = at(2026, 8, 14, 11, 0))
            .copy(categoryId = "groceries", subCategoryId = "supermarket")
        val viewModel = viewModel(
            transactions = listOf(grocery),
            saved = listOf(earlier),
            subCategories = flowOf(listOf(Supermarket)),
        )

        viewModel.uiState.test {
            awaitItem()

            val state = awaitItem() as HomeUiState.Success
            assertEquals("Groceries › Supermarket", state.transactions.single().parts.category)
            assertEquals("Wed 12 Aug · Groceries › Supermarket", state.savedToday.single().meta)
        }
    }

    /** #115's rule for sub-categories: Home keeps its holder, so a rename has to arrive live. */
    @Test
    fun aRenamedSubCategoryReachesTheOpenScreen() = runTest {
        val grocery = saved("bigc", occurred = at(2026, 8, 14, 18, 30), filed = at(2026, 8, 14, 18, 31))
            .copy(categoryId = "groceries", subCategoryId = "supermarket")
        val subCategories = MutableStateFlow(listOf(Supermarket))
        val viewModel = viewModel(transactions = listOf(grocery), subCategories = subCategories)

        viewModel.uiState.test {
            awaitItem()
            assertEquals("Groceries › Supermarket", (awaitItem() as HomeUiState.Success).transactions.single().parts.category)

            subCategories.value = listOf(Supermarket.copy(name = "Big C"))

            assertEquals("Groceries › Big C", (awaitItem() as HomeUiState.Success).transactions.single().parts.category)
        }
    }

    @Test
    fun namesTheBangkokDayThatTheHeroTotalBelongsTo() = runTest {
        val lateOnTheFourteenth = LocalDateTime(2026, 8, 14, 23, 30).toInstant(BangkokTime)
        val viewModel = viewModel(transactions = emptyList(), now = lateOnTheFourteenth)

        viewModel.uiState.test {
            awaitItem()

            val state = awaitItem() as HomeUiState.Success
            assertEquals("Spent today · Fri 14 Aug", state.heroLabel)
        }
    }

    @Test
    fun offersAWayIntoTheQueueThatSaysHowManySlipsAndWhy() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            queued = listOf(
                slip("q1", "MK Restaurant Rama 9", ReviewReason.NewMerchant),
                slip("q2", "PTT Station Phra Ram 2", ReviewReason.AmountUnclear),
            ),
        )

        viewModel.uiState.test {
            awaitItem()

            val review = (awaitItem() as HomeUiState.Success).review
            assertEquals("2 slips to check", review?.title)
            assertEquals("One new shop, one unreadable amount", review?.detail)
        }
    }

    /**
     * Three rows with nothing in the merchant column, meaning different things. A slip whose name
     * the reader missed really is unknown; a row somebody typed never had one, so its category names it (#210).
     */
    @Test
    fun aRowWithNoMerchantSaysWhichKindOfNothingItIs() = runTest {
        val viewModel = viewModel(
            transactions = listOf(
                nameless("scanned", Direction.Expense, TransactionSource.Scan),
                nameless("cash", Direction.Expense, TransactionSource.Manual).copy(categoryId = "food"),
                nameless("salary", Direction.Income, TransactionSource.Manual).copy(categoryId = "income"),
            ),
        )

        viewModel.uiState.test {
            awaitItem()

            val rows = (awaitItem() as HomeUiState.Success).transactions
                .associate { it.id to it.merchant }

            assertEquals("Unknown shop", rows["scanned"])
            assertEquals("Food & drink", rows["cash"])
            assertEquals("Income", rows["salary"])
        }
    }

    /**
     * Today read the category off the row while the Ledger special-cased income, so one restored
     * income row drew two different glyphs depending on which screen you opened it from.
     */
    @Test
    fun anIncomeRowKeepsThePaymentsGlyphWhereSpendingFallsBackToUncategorised() = runTest {
        val viewModel = viewModel(
            transactions = listOf(
                nameless("salary", Direction.Income, TransactionSource.Manual),
                nameless("cash", Direction.Expense, TransactionSource.Manual),
            ),
        )

        viewModel.uiState.test {
            awaitItem()

            val icons = (awaitItem() as HomeUiState.Success).transactions
                .associate { it.id to it.icon }

            assertEquals("payments", icons["salary"])
            assertEquals("more_horiz", icons["cash"])
        }
    }

    private fun nameless(id: String, direction: Direction, source: TransactionSource) = Transaction(
        id = id,
        occurredAtEpochMillis = 0L,
        amount = Satang(48_500),
        direction = direction,
        categoryId = null,
        counterparty = null,
        source = source,
    )

    @Test
    fun aDayWhoseOnlyEntriesAreQueuedSlipsShowsTheEmptyStateNotZero() = runTest {
        val viewModel = viewModel(
            transactions = emptyList(),
            queued = listOf(
                slip("q1", "LAWSON108_P3793 THE LIVIN", ReviewReason.AmountUnclear),
                slip("q2", "SHOPEEPAY", ReviewReason.NewMerchant),
            ),
        )

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            val state = awaitItem() as HomeUiState.Success

            // FR-20: the day has no rows, so the screen says so. A queued slip is money the app
            // refused to count, and rendering it as a zero would report a measurement it never took.
            assertEquals(emptyList(), state.transactions)
            assertEquals(0, state.summary.count)
            // The queue is still offered — the reader has something to do, just not a total to read.
            assertEquals("2 slips to check", state.review?.title)
        }
    }

    private fun slip(id: String, merchant: String, reason: ReviewReason) = QueuedSlip(
        id = id,
        merchant = merchant,
        occurredAtEpochMillis = 0L,
        note = "ref 0142K9",
        amount = if (reason == ReviewReason.AmountUnclear) null else Satang(48_500),
        reason = reason,
        capturedAtEpochMillis = 0L,
    )

    private fun viewModel(
        transactions: List<Transaction>,
        sinceLastImportMillis: Long? = 0L,
        queued: List<QueuedSlip> = emptyList(),
        saved: List<Transaction> = emptyList(),
        now: Instant = LocalDateTime(2026, 8, 14, 12, 0).toInstant(BangkokTime),
        subCategories: Flow<List<SubCategory>> = flowOf(emptyList()),
        runs: List<ImportRun> = emptyList(),
    ) = HomeViewModel(
        ledger = FakeLedger(transactions, sinceLastImportMillis, queued, all = transactions + saved, runs = runs),
        subCategoryUpdates = subCategories,
        clock = object : Clock {
            override fun now(): Instant = now
        },
    )

    private fun transaction(amount: Satang, direction: Direction) = Transaction(
        id = "t${amount.raw}$direction",
        occurredAtEpochMillis = 0L,
        amount = amount,
        direction = direction,
        categoryId = null,
        counterparty = null,
    )

    private fun saved(id: String, occurred: Long, filed: Long) = Transaction(
        id = id,
        occurredAtEpochMillis = occurred,
        amount = Satang(15_500),
        direction = Direction.Expense,
        categoryId = "food",
        counterparty = "LAWSON108_P3793 THE LIVIN",
        source = TransactionSource.Scan,
        filedAt = filed,
    )

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(BangkokTime).toEpochMilliseconds()

    private class FakeLedger(
        transactions: List<Transaction>,
        sinceLastImportMillis: Long?,
        queued: List<QueuedSlip>,
        all: List<Transaction>,
        runs: List<ImportRun>,
    ) : LedgerRepository {
        override val todaysTransactions: StateFlow<LedgerSnapshot> =
            MutableStateFlow(LedgerSnapshot.Rows(transactions))
        override val allTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(all))

        override val recentTransactions: StateFlow<LedgerSnapshot> =
            MutableStateFlow(LedgerSnapshot.Rows(transactions))
        override val sinceLastImportMillis: StateFlow<Long?> =
            MutableStateFlow(sinceLastImportMillis)
        override val importRuns: StateFlow<List<ImportRun>> = MutableStateFlow(runs)

        override val queuedSlips: StateFlow<List<QueuedSlip>> = MutableStateFlow(queued)

        /** Today is a reader. A write reaching here would mean the screen has grown a writer. */

        /** Today never deletes, so a call here is a screen reaching past its job. */
        override suspend fun delete(transactionId: String): Unit = error("Today never deletes")

        override suspend fun restore(transaction: Transaction): Unit = error("Today never deletes")

        override suspend fun applyEdit(transactionId: String, edit: TransactionEdit): Unit =
            error("Today never edits")

        override suspend fun record(draft: TransactionDraft) = error("Today never writes")
    }
}

private val Supermarket = SubCategory("supermarket", "groceries", "Supermarket")

private const val FIVE_MINUTES = 5L * 60 * 1000
