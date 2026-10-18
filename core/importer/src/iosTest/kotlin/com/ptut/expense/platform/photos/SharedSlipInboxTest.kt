@file:OptIn(ExperimentalForeignApi::class)

package com.ptut.expense.platform.photos

import com.ptut.expense.platform.files.documentsSubdirectory
import com.ptut.expense.platform.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.writeToURL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * FR-1's iOS half. The share sheet hands over a file and walks away, so the copy has to be the
 * thing that survives — the URL is not guaranteed to outlive the launch that delivered it.
 */
@OptIn(ExperimentalUuidApi::class)
class SharedSlipInboxTest {

    private val inbox = SharedSlipInbox()
    private val handedOver = mutableListOf<NSURL>()

    @AfterTest
    fun clearInbox() = runTest {
        inbox.candidates().forEach { inbox.delete(it.id) }
        handedOver.forEach { NSFileManager.defaultManager.removeItemAtURL(it, null) }
    }

    @Test
    fun `a shared picture waits in the inbox as its own candidate`() = runTest {
        inbox.accept(handover("a slip"))

        val waiting = inbox.candidates()

        assertEquals(1, waiting.size)
        assertEquals("a slip", inbox.bytesOf(waiting.single().id)?.decodeToString())
    }

    /**
     * US-4: the same picture shared twice is two candidates carrying the same bytes, so tier-one
     * hashing files it once. Reusing a name here would hide that from the importer.
     */
    @Test
    fun `the same picture shared twice waits twice`() = runTest {
        inbox.accept(handover("a slip"))
        inbox.accept(handover("a slip"))

        assertEquals(2, inbox.candidates().size)
    }

    @Test
    fun `a filed slip's picture goes with it`() = runTest {
        inbox.accept(handover("a slip"))
        val waiting = inbox.candidates().single()

        inbox.delete(waiting.id)

        assertEquals(emptyList(), inbox.candidates())
    }

    @Test
    fun `an empty file is refused with a reason rather than filed as a slip`() = runTest {
        val failure = assertFails { inbox.accept(handover("")) }

        assertTrue(failure.message.orEmpty().contains("nothing to read"), failure.message.orEmpty())
    }

    /**
     * Ids come back through Room and through the queued rows of older builds, so a separator in
     * one would otherwise be a way out of the inbox folder.
     */
    @Test
    fun `an id that climbs out of the inbox reads as nothing at all`() = runTest {
        assertNull(inbox.bytesOf("../Library/Preferences"))
        assertNull(inbox.bytesOf(".."))
        assertNull(inbox.bytesOf(""))
    }

    /**
     * iOS expects the app to clear its own handover directory, or every shared slip stays on disk
     * twice for good.
     */
    @Test
    fun `the copy iOS left behind is cleared once the slip is safely in`() = runTest {
        val handover = systemHandover("a slip")

        inbox.accept(handover)

        assertFalse(
            NSFileManager.defaultManager.fileExistsAtPath(requireNotNull(handover.path)),
            "the system inbox would grow a copy of every slip ever shared",
        )
    }

    /**
     * A URL arrives from outside this app, so the rule is the directory and not the container.
     * Widening it would put the ledger's own file inside the blast radius of a shared picture.
     */
    @Test
    fun `a picture opened somewhere else is copied and left alone`() = runTest {
        val elsewhere = handover("a slip")

        inbox.accept(elsewhere)

        assertTrue(
            NSFileManager.defaultManager.fileExistsAtPath(requireNotNull(elsewhere.path)),
            "this app deleted a file it was only shown",
        )
    }

    /** The real thing: `Documents/Inbox`, which is where iOS itself puts a shared file. */
    private fun systemHandover(contents: String): NSURL {
        val landing = documentsSubdirectory("Inbox").URLByAppendingPathComponent(Uuid.random().toString())
        val file = requireNotNull(landing)
        contents.encodeToByteArray().toNSData().writeToURL(file, atomically = true)
        handedOver += file
        return file
    }

    /** A picture this app was shown rather than handed — the temporary directory stands in. */
    private fun handover(contents: String): NSURL {
        val file = NSURL.fileURLWithPath(NSTemporaryDirectory() + Uuid.random().toString())
        contents.encodeToByteArray().toNSData().writeToURL(file, atomically = true)
        handedOver += file
        return file
    }
}
