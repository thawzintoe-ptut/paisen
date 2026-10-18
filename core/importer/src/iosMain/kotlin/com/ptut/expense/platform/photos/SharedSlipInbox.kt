@file:OptIn(ExperimentalForeignApi::class)

package com.ptut.expense.platform.photos

import com.ptut.expense.locale.AppText
import com.ptut.expense.platform.files.documentsDirectory
import com.ptut.expense.platform.files.documentsSubdirectory
import com.ptut.expense.platform.toByteArray
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToURL
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Documents, not Caches: a slip waiting to be read is evidence, and the system empties caches. */
private const val INBOX_FOLDER = "shared-slips"

/** Where iOS itself puts a file handed to this app. Its name is the system's, not this app's. */
private const val SYSTEM_HANDOVER_FOLDER = "Inbox"

private const val MILLIS_PER_SECOND = 1_000

/**
 * FR-1: the pictures the share sheet handed over, waiting for the next import run.
 *
 * The bytes are copied in at once rather than the URL being kept. iOS drops a shared file into the
 * app's own inbox and expects the app to take it from there — the URL is not guaranteed to survive
 * the launch that delivered it, and the import may run long after.
 *
 * It is a [SlipSource] like the album scan and not a path of its own, which is what makes US-4
 * true: a slip shared by hand and the same slip found by the scan are two candidates with the same
 * bytes, so the importer's hash sees the second one for what it is.
 */
class SharedSlipInbox : SlipSource {

    private val inbox by lazy { documentsSubdirectory(INBOX_FOLDER) }

    /**
     * Copies one shared picture in. Throws with a line written for the reader, because the share
     * sheet has already closed by the time this runs and a toast is all that is left to say it.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun accept(picture: NSURL) = withContext(Dispatchers.Default) {
        // A file handed over in place is only readable inside its security scope, and a file
        // already copied into this app's inbox opens without one. Both answer this call.
        val scoped = picture.startAccessingSecurityScopedResource()
        val bytes = try {
            NSData.dataWithContentsOfURL(picture)
        } finally {
            if (scoped) picture.stopAccessingSecurityScopedResource()
        }

        if (bytes == null || bytes.length == 0uL) {
            error(AppText.copy.slips.shareEmpty)
        }

        val landing = slipAt(Uuid.random().toString()) ?: error(AppText.copy.slips.shareNotFiled)
        if (!bytes.writeToURL(landing, atomically = true)) {
            error(AppText.copy.slips.shareNotSaved)
        }
        discardHandover(picture)
    }

    override suspend fun candidates(): List<SlipCandidate> = withContext(Dispatchers.Default) {
        inboxContents()
            .mapNotNull { slip ->
                val name = slip.lastPathComponent ?: return@mapNotNull null
                SlipCandidate(id = name, capturedAtEpochMillis = slip.modifiedAtEpochMillis())
            }
            .sortedBy { it.capturedAtEpochMillis }
    }

    override suspend fun bytesOf(id: String): ByteArray? = withContext(Dispatchers.Default) {
        val slip = slipAt(id) ?: return@withContext null
        NSData.dataWithContentsOfURL(slip)?.toByteArray()
    }

    override suspend fun delete(id: String) {
        withContext(Dispatchers.Default) {
            slipAt(id)?.let { NSFileManager.defaultManager.removeItemAtURL(it, null) }
        }
    }

    /**
     * iOS leaves the handover copy in `Documents/Inbox` and expects the app to clear it.
     *
     * That one directory and not the whole container. A URL arrives from outside this app, and
     * "delete whatever it points at" would put the ledger itself inside the blast radius — nothing
     * but a handover ever sits in the system's inbox.
     */
    private fun discardHandover(picture: NSURL) {
        val handover = documentsDirectory().URLByAppendingPathComponent(SYSTEM_HANDOVER_FOLDER)
        if (picture.parentFolder() != handover?.path?.asFolder()) return

        NSFileManager.defaultManager.removeItemAtURL(picture, null)
    }

    private fun inboxContents(): List<NSURL> = NSFileManager.defaultManager
        .contentsOfDirectoryAtURL(inbox, null, options = 0uL, error = null)
        .orEmpty()
        .filterIsInstance<NSURL>()

    /**
     * Null for anything that is not a plain name inside the inbox. Ids come back through Room and
     * through the queued rows of older builds, so a separator here would be a way out of the folder.
     */
    private fun slipAt(id: String): NSURL? =
        if (id.isEmpty() || '/' in id || id == "." || id == "..") {
            null
        } else {
            inbox.URLByAppendingPathComponent(id)
        }
}

/** A trailing slash is not part of a directory's identity, and only one of these two carries one. */
private fun NSURL.parentFolder(): String? = URLByDeletingLastPathComponent?.path?.asFolder()

private fun String.asFolder(): String = trimEnd('/')

/** NFR-4 dates a slip by when it arrived, which for a handover is when it was written here. */
private fun NSURL.modifiedAtEpochMillis(): Long {
    val attributes = path?.let { NSFileManager.defaultManager.attributesOfItemAtPath(it, null) }
    val modified = attributes?.get(NSFileModificationDate) as? NSDate ?: return 0L
    return (modified.timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()
}
