package com.ptut.expense.activity

import app.cash.turbine.test
import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.ImportOutcome
import com.ptut.expense.domain.ImportRun
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.ReviewReason
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ActivityViewModelTest : ViewModelTest() {

    @Test
    fun `a run that scanned and found nothing reads as a success`() = runTest {
        entries(runs = listOf(run(id = "r1", outcome = ImportOutcome.Success, slipsFound = 0))) { entries ->
            val entry = entries.single()
            // US-8: the pipe worked. Only a run that could not scan is a failure.
            assertEquals("Checked, nothing new", entry.title)
            assertEquals(ActivityTone.Reported, entry.tone)
        }
    }

    @Test
    fun `a run that could not scan reads as a failure and carries the reason`() = runTest {
        entries(
            runs = listOf(
                run(
                    id = "r2",
                    outcome = ImportOutcome.Failure,
                    reason = "The slip source is not wired up on this platform yet",
                ),
            ),
        ) { entries ->
            val entry = entries.single()
            assertEquals("Could not read the folder", entry.title)
            assertEquals(ActivityTone.NeedsYou, entry.tone)
            assertTrue(entry.body.contains("not wired up"), entry.body)
        }
    }

    @Test
    fun `a run that filed slips counts them and one slip is not plural`() = runTest {
        entries(
            runs = listOf(
                run(id = "many", outcome = ImportOutcome.Success, slipsFound = 4, filed = 4),
                run(id = "one", outcome = ImportOutcome.Success, slipsFound = 1, filed = 1, hour = 8),
            ),
        ) { entries ->
            assertEquals(listOf("Imported 4 slips", "Imported 1 slip"), entries.map { it.title })
            // Slips that landed get their own tone, so the good news is findable at a glance.
            assertTrue(entries.all { it.tone == ActivityTone.Imported }, entries.toString())
        }
    }

    @Test
    fun `a slip whose amount could not be read is the one thing that takes the needs-you tone`() = runTest {
        entries(
            queued = listOf(queued(id = "q1", reason = ReviewReason.AmountUnclear, merchant = "PTT Station")),
        ) { entries ->
            val entry = entries.single()
            assertEquals("A slip needs an amount", entry.title)
            assertTrue(entry.body.startsWith("PTT Station · "), entry.body)
            assertEquals(ActivityTone.NeedsYou, entry.tone)
        }
    }

    @Test
    fun `a new shop was asked once so it reports rather than warns`() = runTest {
        entries(
            queued = listOf(queued(id = "q1", reason = ReviewReason.NewMerchant, merchant = "MK Restaurant")),
        ) { entries ->
            val entry = entries.single()
            assertEquals("A slip needs a category", entry.title)
            assertTrue(entry.body.startsWith("MK Restaurant · "), entry.body)
            assertTrue(entry.body.endsWith("added to To check"), entry.body)
            // The design draws it neutral here and on the review queue. Waiting for a category is
            // not the same kind of work as a slip the reader has to unstick.
            assertEquals(ActivityTone.Reported, entry.tone)
        }
    }

    @Test
    fun `the journal and the queue read as one list newest first`() = runTest {
        entries(
            runs = listOf(
                run(id = "early", outcome = ImportOutcome.Success, slipsFound = 0, hour = 8),
                run(id = "late", outcome = ImportOutcome.Success, slipsFound = 2, hour = 20),
            ),
            queued = listOf(queued(id = "q1", reason = ReviewReason.AmountUnclear, hour = 14)),
        ) { entries ->
            assertEquals(listOf("late", "queued-q1", "early"), entries.map { it.id })
        }
    }

    @Test
    fun `an unfinished run is stamped from when it started since it has no end yet`() = runTest {
        entries(
            runs = listOf(run(id = "r3", outcome = ImportOutcome.Running, hour = 9, finished = false)),
        ) { entries ->
            val entry = entries.single()
            assertEquals("Import in progress", entry.title)
            // p23: earlier today reads as the clock alone.
            assertEquals("09:00", entry.time)
        }
    }

    private suspend fun entries(
        runs: List<ImportRun> = emptyList(),
        queued: List<QueuedSlip> = emptyList(),
        assert: (List<ActivityEntryUiModel>) -> Unit,
    ) = ActivityViewModel(JournalLedger(runs, queued), clock = frozen).uiState.test {
        var state = awaitItem()
        while (state.entries.isEmpty()) state = awaitItem()
        assert(state.entries)
        cancelAndIgnoreRemainingEvents()
    }

    private fun run(
        id: String,
        outcome: ImportOutcome,
        slipsFound: Int = 0,
        filed: Int = 0,
        reason: String? = null,
        hour: Int = 12,
        finished: Boolean = true,
    ) = ImportRun(
        id = id,
        startedAtEpochMillis = at(hour),
        finishedAtEpochMillis = if (finished) at(hour) else null,
        outcome = outcome,
        slipsFound = slipsFound,
        failureReason = reason,
        filed = filed,
    )

    private fun queued(
        id: String,
        reason: ReviewReason,
        merchant: String = "Lawson 108",
        hour: Int = 12,
    ) = QueuedSlip(
        id = id,
        merchant = merchant,
        amount = Satang(2_825),
        occurredAtEpochMillis = at(hour),
        note = "",
        reason = reason,
        capturedAtEpochMillis = at(hour),
    )

    private companion object {
        /** Midday in Bangkok, so "Today" in the stamps is the same day the entries carry. */
        val frozen = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(at(23))
        }

        fun at(hour: Int): Long =
            LocalDateTime(2026, 8, 21, hour, 0).toInstant(BangkokTime).toEpochMilliseconds()
    }
}

private class JournalLedger(
    runs: List<ImportRun>,
    queued: List<QueuedSlip>,
) : LedgerRepository {

    override val todaysTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(emptyList()))
    override val recentTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(emptyList()))
    override val allTransactions: StateFlow<LedgerSnapshot> = MutableStateFlow(LedgerSnapshot.Rows(emptyList()))
    override val sinceLastImportMillis: StateFlow<Long?> = MutableStateFlow(null)
    override val importRuns: StateFlow<List<ImportRun>> = MutableStateFlow(runs)
    override val queuedSlips: StateFlow<List<QueuedSlip>> = MutableStateFlow(queued)

    /** Activity never deletes, so a call here is a screen reaching past its job. */

    override suspend fun delete(transactionId: String): Unit = error("Activity never deletes")

    override suspend fun restore(transaction: Transaction): Unit = error("Activity never deletes")

    override suspend fun applyEdit(transactionId: String, edit: TransactionEdit): Unit =
        error("Activity never edits")

    override suspend fun record(draft: TransactionDraft) = Unit
}
