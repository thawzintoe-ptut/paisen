package com.ptut.expense.review

import app.cash.turbine.test
import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.TransactionSource
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.slip.QueuedSlipEntry
import com.ptut.expense.importer.FakeSlipSource
import com.ptut.expense.importer.FakeSlipTextReader
import com.ptut.expense.importer.InMemoryImportStore
import com.ptut.expense.importer.SlipDiscards
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.ui.DisplayAmount
import com.ptut.expense.ui.ToastMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ReviewViewModelTest : ViewModelTest() {

    private val now = LocalDateTime(2026, 8, 14, 19, 41).toInstant(BangkokTime)

    @Test
    fun `an unread amount reads not counted and never a number and never a zero`() = runTest {
        val viewModel = viewModel(listOf(slip("q1", amount = null)))

        viewModel.uiState.test {
            val state = awaitItem().takeIf { it.slips.isNotEmpty() } ?: awaitItem()

            assertEquals(DisplayAmount.NotCounted, state.slips.single().amount)
        }
    }

    @Test
    fun `a slip the reader could count carries the exact amount`() = runTest {
        val viewModel = viewModel(listOf(slip("q1", amount = Satang(48_500))))

        viewModel.uiState.test {
            val state = awaitItem().takeIf { it.slips.isNotEmpty() } ?: awaitItem()

            assertEquals(DisplayAmount.Exact(Satang(48_500)), state.slips.single().amount)
        }
    }

    @Test
    fun `the card carries no domain object and only the strings the screen can draw`() = runTest {
        val viewModel = viewModel(listOf(slip("q1", amount = null, merchant = "SHOPEEPAY")))

        viewModel.uiState.test {
            val card = (awaitItem().takeIf { it.slips.isNotEmpty() } ?: awaitItem()).slips.single()

            assertEquals("q1", card.id)
            assertEquals("SHOPEEPAY", card.merchant)
            assertEquals("14 Aug 19:41 · ref 0142K9", card.meta)
            assertEquals(ReviewReason.AmountUnclear, card.reason)
        }
    }

    // NFR-4

    @Test
    fun `the note names the days the oldest slip has left rather than the number in the spec`() = runTest {
        val viewModel = viewModel(
            listOf(
                slip("fresh", amount = null, capturedAt = now),
                slip("old", amount = null, capturedAt = now - 5.days),
            ),
        )

        viewModel.uiState.test {
            val state = awaitItem().takeIf { it.slips.isNotEmpty() } ?: awaitItem()

            assertTrue(
                "The oldest here has 2 days left." in state.retentionNote,
                "the note quoted the wrong slip: ${state.retentionNote}",
            )
        }
    }

    @Test
    fun `a slip whose image goes today says so rather than counting down to zero`() = runTest {
        val viewModel = viewModel(listOf(slip("old", amount = null, capturedAt = now - 7.days)))

        viewModel.uiState.test {
            val state = awaitItem().takeIf { it.slips.isNotEmpty() } ?: awaitItem()

            assertTrue(
                "loses its image today" in state.retentionNote,
                "the note counted to zero instead: ${state.retentionNote}",
            )
        }
    }

    @Test
    fun `an empty queue promises nothing about days`() = runTest {
        val viewModel = viewModel(emptyList())

        viewModel.uiState.test {
            val state = awaitItem()

            assertEquals(emptyList(), state.slips)
            assertTrue("days left" !in state.retentionNote)
            assertTrue("today" !in state.retentionNote)
        }
    }

    // FR-20 — the empty state has to prove the importer is alive

    @Test
    fun `an empty queue counts what was filed without asking`() = runTest {
        val viewModel = viewModel(
            queued = emptyList(),
            filed = listOf(scanned("t1"), scanned("t2"), scanned("t3")),
        )

        viewModel.uiState.test {
            val state = awaitItem().takeIf { it.filedAutomatically > 0 } ?: awaitItem()

            // The design's line is "Last 38 slips filed automatically" — the number is live, so a
            // bare queue still says the importer is working rather than just looking broken.
            assertEquals(3, state.filedAutomatically)
        }
    }

    @Test
    fun `a hand-typed row is not counted as filed automatically`() = runTest {
        val viewModel = viewModel(queued = emptyList(), filed = listOf(typed("t1"), scanned("t2")))

        viewModel.uiState.test {
            val state = awaitItem().takeIf { it.filedAutomatically > 0 } ?: awaitItem()

            assertEquals(1, state.filedAutomatically)
        }
    }

    // FR-8

    @Test
    fun `discarding drops the row and the picture with it`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore()
        source.add("q1", "irrelevant", now.toEpochMilliseconds())
        store.queue(entry("q1"))

        viewModel(listOf(slip("q1", amount = null)), source, store).discard("q1")
        runCurrent()

        assertEquals(emptyList(), store.queued)
        assertTrue(source.remaining.isEmpty(), "the picture outlived the row that explained it")
    }

    private fun viewModel(
        queued: List<QueuedSlip>,
        source: FakeSlipSource = FakeSlipSource(),
        store: InMemoryImportStore = InMemoryImportStore(),
        filed: List<Transaction> = emptyList(),
    ) = ReviewViewModel(
        ledger = FakeLedger(queued, filed),
        discards = SlipDiscards(SlipImporter(source, FakeSlipTextReader(), store), ToastMessages(), CoroutineScope(Dispatchers.Main)) { Duration.ZERO },
        clock = object : Clock {
            override fun now(): Instant = this@ReviewViewModelTest.now
        },
    )

    private fun slip(
        id: String,
        amount: Satang?,
        merchant: String = "LAWSON108_P3793 THE LIVIN",
        capturedAt: Instant = now,
    ) = QueuedSlip(
        id = id,
        merchant = merchant,
        occurredAtEpochMillis = now.toEpochMilliseconds(),
        note = "ref 0142K9",
        amount = amount,
        reason = if (amount == null) ReviewReason.AmountUnclear else ReviewReason.NewMerchant,
        capturedAtEpochMillis = capturedAt.toEpochMilliseconds(),
    )

    private fun entry(id: String) = QueuedSlipEntry(
        id = id,
        hash = "hash-$id",
        counterparty = "LAWSON108_P3793 THE LIVIN",
        amount = null,
        occurredAtEpochMillis = now.toEpochMilliseconds(),
        reference = null,
        capturedAtEpochMillis = now.toEpochMilliseconds(),
        reason = ReviewReason.AmountUnclear,
    )

    private fun scanned(id: String) = transaction(id, TransactionSource.Scan)

    private fun typed(id: String) = transaction(id, TransactionSource.Manual)

    private fun transaction(id: String, source: TransactionSource) = Transaction(
        id = id,
        occurredAtEpochMillis = now.toEpochMilliseconds(),
        amount = Satang(1_000),
        direction = Direction.Expense,
        categoryId = "food",
        counterparty = "LAWSON108_P3793 THE LIVIN",
        source = source,
    )

    private class FakeLedger(queued: List<QueuedSlip>, filed: List<Transaction>) : LedgerRepository {
        override val todaysTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(filed))
        override val recentTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(filed))

        override val allTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(filed))
        override val sinceLastImportMillis: StateFlow<Long?> = MutableStateFlow(0L)
        override val importRuns: StateFlow<List<ImportRun>> = MutableStateFlow(emptyList())

        override val queuedSlips: StateFlow<List<QueuedSlip>> = MutableStateFlow(queued)

        /** The review queue never deletes, so a call here is a screen reaching past its job. */

        override suspend fun delete(transactionId: String): Unit = error("The review queue never deletes")

        override suspend fun restore(transaction: Transaction): Unit = error("The review queue never deletes")

        override suspend fun applyEdit(transactionId: String, edit: TransactionEdit): Unit =
            error("The review queue never edits")

        override suspend fun record(draft: TransactionDraft) = Unit
    }
}
