package com.ptut.expense.settings

import app.cash.turbine.test
import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.backup.BackupPreview
import com.ptut.expense.domain.backup.BackupUnreadable
import com.ptut.expense.domain.backup.LedgerArchive
import com.ptut.expense.domain.backup.MergeReport
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.platform.files.LedgerFileStore
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class LedgerBackupViewModelTest : ViewModelTest() {

    private val toasts = ToastMessages()
    private val now = LocalDateTime(2026, 8, 24, 19, 41).toInstant(BangkokTime)

    @Test
    fun `export hands the archive's file to the store and says where it landed`() = runTest {
        val files = RecordingFiles(writeLandsAt = "Downloads/ptut-ledger.json")
        val backup = viewModel(files = files)

        toasts.messages.test {
            backup.export()
            runCurrent()

            assertEquals("the ledger as json", files.written)
            val toast = awaitItem()
            assertEquals("Saved to Downloads/ptut-ledger.json", toast.text)
            assertEquals(ToastTone.Success, toast.tone)
        }
    }

    @Test
    fun `backing out of the picker says nothing at all`() = runTest {
        val backup = viewModel(files = RecordingFiles(writeLandsAt = null))

        toasts.messages.test {
            backup.export()
            runCurrent()

            expectNoEvents()
        }
    }

    /** The Bangkok date, so two exports on the same evening in two zones do not disagree. */
    @Test
    fun `the file is named for the Bangkok day it was written on`() = runTest {
        assertEquals("ptut-ledger-2026-08-24.json", state().fileName)
    }

    @Test
    fun `the screen counts what will go into the file before it opens a picker`() = runTest {
        val ledger = FakeLedger(listOf(row("a", day = 3), row("b", day = 21)))

        assertEquals("2 transactions, back to 3 Aug", state(ledger).holding)
    }

    @Test
    fun `one row is counted in the singular`() = runTest {
        assertEquals("1 transaction, back to 3 Aug", state(FakeLedger(listOf(row("a", day = 3)))).holding)
    }

    @Test
    fun `an empty ledger says there is nothing to export and offers no file`() = runTest {
        val state = state(FakeLedger())

        assertEquals("Nothing to export yet", state.holding)
        assertFalse(state.exportable, "an empty file imports back as nothing and looks like a bug")
    }

    @Test
    fun `a ledger that has not answered offers no file either`() = runTest {
        assertFalse(state(FakeLedger.unanswered()).exportable)
    }

    @Test
    fun `a ledger with rows in it is exportable`() = runTest {
        assertTrue(state(FakeLedger(listOf(row("a", day = 3)))).exportable)
    }

    /** A zero here would stop the reader tapping export on a ledger that is not actually empty. */
    @Test
    fun `a ledger that has not answered is counting rather than empty`() = runTest {
        assertEquals("Counting what you have…", state(FakeLedger.unanswered()).holding)
    }

    @Test
    fun `import reports what was added and what the ledger already held`() = runTest {
        val backup = viewModel(
            files = RecordingFiles(reads = "a file"),
            archive = FakeArchive(report = MergeReport(added = 3, alreadyHeld = 2)),
        )

        toasts.messages.test {
            backup.chooseFile()
            runCurrent()
            backup.restore()
            runCurrent()

            assertEquals("Added 3 transactions · 2 already here", awaitItem().text)
        }
    }

    @Test
    fun `a file the ledger already held is a success rather than a zero`() = runTest {
        val backup = viewModel(
            files = RecordingFiles(reads = "a file"),
            archive = FakeArchive(report = MergeReport(added = 0, alreadyHeld = 7)),
        )

        toasts.messages.test {
            backup.chooseFile()
            runCurrent()
            backup.restore()
            runCurrent()

            val toast = awaitItem()
            assertEquals("Nothing new — your history already held all 7", toast.text)
            assertEquals(ToastTone.Success, toast.tone)
        }
    }

    @Test
    fun `a file this build cannot read refuses in the error tone and gives the reason`() = runTest {
        val backup = viewModel(
            files = RecordingFiles(reads = "not a ledger"),
            archive = FakeArchive(
                failsWith = BackupUnreadable("This file was written by a different version of the app (format 2)"),
            ),
        )

        toasts.messages.test {
            backup.chooseFile()
            runCurrent()

            val toast = awaitItem()
            assertEquals(ToastTone.Error, toast.tone)
            assertTrue(toast.text.contains("format 2"), toast.text)
        }
    }

    @Test
    fun `a second tap while a picker is open is ignored`() = runTest {
        val files = RecordingFiles(writeLandsAt = "somewhere.json")
        val backup = viewModel(files = files)

        backup.export()
        backup.export()
        runCurrent()

        assertEquals(1, files.writes)
    }

    /**
     * Shared `WhileSubscribed`, so `uiState.value` is the initial value until something collects.
     * Reading it straight would answer for an unanswered ledger every time.
     */
    private fun TestScope.state(ledger: LedgerRepository = FakeLedger()): LedgerBackupUiState {
        val uiState = viewModel(ledger = ledger).uiState
        backgroundScope.launch { uiState.collect { } }
        runCurrent()
        return uiState.value
    }

    private fun row(id: String, day: Int) = Transaction(
        id = id,
        occurredAtEpochMillis = LocalDateTime(2026, 8, day, 12, 0).toInstant(BangkokTime).toEpochMilliseconds(),
        amount = Satang(10_000),
        direction = Direction.Expense,
        categoryId = null,
        counterparty = null,
    )

    private fun viewModel(
        ledger: LedgerRepository = FakeLedger(),
        files: LedgerFileStore = RecordingFiles(),
        archive: LedgerArchive = FakeArchive(),
    ) = LedgerBackupViewModel(
        ledger = ledger,
        archive = archive,
        files = files,
        toasts = toasts,
        clock = FixedClock(now),
    )

    private class FixedClock(private val at: Instant) : Clock {
        override fun now(): Instant = at
    }
}

private class FakeArchive(
    private val report: MergeReport = MergeReport(added = 0, alreadyHeld = 0),
    private val failsWith: Exception? = null,
) : LedgerArchive {

    override suspend fun export(): String = "the ledger as json"

    override suspend fun preview(json: String): BackupPreview =
        failsWith?.let { throw it } ?: BackupPreview(transactions = 0, oldestEpochMillis = null, groups = 0, categories = 0, subCategories = 0, shopRules = 0)

    override suspend fun restore(json: String): MergeReport = failsWith?.let { throw it } ?: report
}

private class RecordingFiles(
    private val writeLandsAt: String? = "somewhere.json",
    private val reads: String? = null,
) : LedgerFileStore {

    var written: String? = null
        private set

    var writes = 0
        private set

    override suspend fun write(suggestedName: String, contents: String): String? {
        writes++
        written = contents
        return writeLandsAt
    }

    override suspend fun read(): String? = reads
}
