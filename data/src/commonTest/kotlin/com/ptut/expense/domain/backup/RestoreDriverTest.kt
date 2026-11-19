package com.ptut.expense.domain.backup

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RestoreDriverTest {

    private val holds = BackupPreview(transactions = 2, oldestEpochMillis = null, groups = 0, categories = 0, subCategories = 0, shopRules = 0)
    private val report = MergeReport(added = 2, alreadyHeld = 0)

    @Test
    fun `a file that reads waits on its preview and merges on import`() = runTest {
        val archive = FakeArchive()
        val driver = RestoreDriver(archive)

        driver.read("a file")
        assertEquals(RestoreStep.Preview(holds), driver.step.value)

        assertEquals(report, driver.import())
        assertEquals(RestoreStep.Done(report), driver.step.value)
        assertEquals(listOf("a file"), archive.restored)
    }

    @Test
    fun `a refused preview names the problem and leaves nothing to import`() = runTest {
        val archive = FakeArchive(previewFails = BackupUnreadable(BackupProblem.OtherVersion(9)))
        val driver = RestoreDriver(archive)

        driver.read("a newer file")

        assertEquals(RestoreStep.Refused(BackupProblem.OtherVersion(9)), driver.step.value)
        assertNull(driver.import())
        assertEquals(emptyList(), archive.restored)
    }

    @Test
    fun `a file refused at the merge is refused and not done`() = runTest {
        val driver = RestoreDriver(FakeArchive(restoreFails = BackupUnreadable(BackupProblem.OtherHome("MMK", "THB"))))

        driver.read("a kyat file")

        assertNull(driver.import())
        assertEquals(RestoreStep.Refused(BackupProblem.OtherHome("MMK", "THB")), driver.step.value)
    }

    @Test
    fun `a failure that is not a refusal goes back to idle and is thrown`() = runTest {
        val driver = RestoreDriver(FakeArchive(previewFails = IllegalStateException("disk")))

        assertFailsWith<IllegalStateException> { driver.read("a file") }

        assertEquals(RestoreStep.Idle, driver.step.value)
    }

    @Test
    fun `cancel lets the file go`() = runTest {
        val archive = FakeArchive()
        val driver = RestoreDriver(archive)
        driver.read("a file")

        driver.cancel()

        assertEquals(RestoreStep.Idle, driver.step.value)
        assertNull(driver.import())
        assertEquals(emptyList(), archive.restored)
    }

    @Test
    fun `the wrapper runs around the merge`() = runTest {
        val order = mutableListOf<String>()
        val driver = RestoreDriver(FakeArchive(onRestore = { order += "merge" }))
        driver.read("a file")

        driver.import { merge ->
            order += "before"
            merge()
        }

        assertEquals(listOf("before", "merge"), order)
    }

    private inner class FakeArchive(
        private val previewFails: Exception? = null,
        private val restoreFails: Exception? = null,
        private val onRestore: () -> Unit = {},
    ) : LedgerArchive {
        val restored = mutableListOf<String>()

        override suspend fun export(): String = ""

        override suspend fun preview(json: String): BackupPreview = previewFails?.let { throw it } ?: holds

        override suspend fun restore(json: String): MergeReport {
            restoreFails?.let { throw it }
            onRestore()
            restored += json
            return report
        }
    }
}
