package com.ptut.expense.importer

import com.ptut.expense.domain.folders.FolderReadTally
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FR-5's counter. The badge promises a slip was read here, so what is counted is a reading —
 * not an image seen, and not a picture recognised from a run that already counted it.
 */
class SlipImporterFolderTallyTest {

    private val tally = RecordingTally()

    @Test
    fun `a slip read is counted against the folder that listed it`() = runTest {
        val source = FakeSlipSource()
        source.add("a", slip("7-Eleven", "TX1", "60.00"), capturedAtEpochMillis = 1L, folderId = "kplus")
        source.add("b", slip("Foodland", "TX2", "80.00"), capturedAtEpochMillis = 2L, folderId = "kplus")
        source.add("c", slip("Grab", "TX3", "90.00"), capturedAtEpochMillis = 3L, folderId = "scb")

        importer(source).run()

        assertEquals(mapOf("kplus" to 2, "scb" to 1), tally.read)
    }

    @Test
    fun `a slip from no folder is counted against none`() = runTest {
        val source = FakeSlipSource()
        source.add("shared", slip("7-Eleven", "TX1", "60.00"), capturedAtEpochMillis = 1L)

        importer(source).run()

        assertTrue(tally.read.isEmpty())
    }

    @Test
    fun `a rescan does not count the same picture twice`() = runTest {
        val source = FakeSlipSource()
        source.add("a", slip("7-Eleven", "TX1", "60.00"), capturedAtEpochMillis = 1L, folderId = "kplus")
        val store = InMemoryImportStore()

        importer(source, store).run()
        // The picture survives a scan the platform would not let this app delete, so the next run
        // lists it again and the hash journal is what stops it being read a second time.
        source.add("a", slip("7-Eleven", "TX1", "60.00"), capturedAtEpochMillis = 1L, folderId = "kplus")
        importer(source, store).run()

        assertEquals(1, tally.read["kplus"])
    }

    @Test
    fun `an image that is not a slip is not counted as read`() = runTest {
        val source = FakeSlipSource()
        source.add("noise", "a screenshot of nothing in particular", capturedAtEpochMillis = 1L, folderId = "shots")

        importer(source).run()

        assertTrue(tally.read.isEmpty())
    }

    private fun importer(source: FakeSlipSource, store: InMemoryImportStore = InMemoryImportStore()) =
        SlipImporter(source = source, reader = FakeSlipTextReader(), store = store, folders = tally)

    private fun slip(shop: String, reference: String, amount: String) = """
        Payment Completed
        15 Aug 26  9:25 AM
        MR. SOMCHAI J
        KBank
        xxx-x-xxxxx-x
        $shop
        Transaction ID:
        $reference
        Amount:
        $amount Baht
        Fee:
        0.00 Baht
    """.trimIndent()

    private class RecordingTally : FolderReadTally {
        val read = mutableMapOf<String, Int>()

        override suspend fun addSlipsRead(id: String, read: Int) {
            this.read[id] = (this.read[id] ?: 0) + read
        }
    }
}
