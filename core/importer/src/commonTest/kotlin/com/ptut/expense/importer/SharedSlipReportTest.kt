package com.ptut.expense.importer

import app.cash.turbine.test
import com.ptut.expense.locale.AppText
import com.ptut.expense.platform.ocr.SlipTextReader
import com.ptut.expense.platform.photos.SlipSource
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/** #11: after "Slip received", a slip shared in by hand always says what happened to it. */
class SharedSlipReportTest {

    private val text = AppText.copy.slips
    private val received = ToastMessage(text.shareReceived)
    private val lookIn = ToastMessage(text.shareLanded(text.shareLookIn))

    @Test
    fun `a slip the reader fails on says it is tried again on the next scan`() = runTest {
        val source = inbox()
        val importer = SlipImporter(source, FailingReader, InMemoryImportStore())

        assertToasts(importer, inboxId = INBOX_ID, received, ToastMessage(text.shareLanded(text.shareNotReadYet)))
    }

    @Test
    fun `a picture that went before it was read says so`() = runTest {
        val importer = SlipImporter(GoneBeforeRead(inbox()), FakeSlipTextReader(), InMemoryImportStore())

        assertToasts(importer, inboxId = INBOX_ID, received, ToastMessage(text.shareGone, ToastTone.Error))
    }

    /** With no id to wait for, the first line already says where to look. */
    @Test
    fun `a picture that cannot be told apart says where the slip will show`() = runTest {
        val importer = SlipImporter(inbox(), FakeSlipTextReader(), InMemoryImportStore())

        assertToasts(importer, inboxId = null, lookIn)
    }

    @Test
    fun `a run slower than the wait says where the slip will show`() = runTest {
        val importer = SlipImporter(inbox(), FakeSlipTextReader(), InMemoryImportStore())
        val toasts = ToastMessages()

        toasts.messages.test {
            // The scan never runs, as when a long folder batch holds the run.
            backgroundScope.launch { importer.tellWhereSharedSlipWent(INBOX_ID, toasts, scan = {}) }
            assertEquals(received, awaitItem())
            advanceTimeBy(2.minutes)
            expectNoEvents()
            advanceTimeBy(1)
            assertEquals(lookIn, awaitItem())
        }
    }

    private suspend fun TestScope.assertToasts(importer: SlipImporter, inboxId: String?, vararg expected: ToastMessage) {
        val toasts = ToastMessages()
        toasts.messages.test {
            backgroundScope.launch {
                importer.tellWhereSharedSlipWent(inboxId, toasts, scan = { backgroundScope.launch { importer.run() } })
            }
            expected.forEach { assertEquals(it, awaitItem()) }
            expectNoEvents()
        }
    }

    private fun inbox() = FakeSlipSource().apply { add(INBOX_ID, SlipTranscripts.readable, capturedAtEpochMillis = 1_755_000_000_000) }

    private object FailingReader : SlipTextReader {
        override suspend fun read(bytes: ByteArray): List<String> = error("the text reader failed")
    }

    /** Listed by the inbox, then deleted before the run reached it. */
    private class GoneBeforeRead(private val inner: FakeSlipSource) : SlipSource by inner {
        override suspend fun bytesOf(id: String): ByteArray? = null
    }

    private companion object {
        const val INBOX_ID = "inbox-1"
    }
}
