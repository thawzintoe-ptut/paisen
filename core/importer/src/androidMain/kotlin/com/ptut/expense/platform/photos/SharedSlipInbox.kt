package com.ptut.expense.platform.photos

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** `filesDir`, not `cacheDir`: a slip waiting to be read is evidence, and the system empties caches. */
private const val INBOX_FOLDER = "shared-slips"

/** A phone screenshot is a few megabytes. Past this it is not a slip, and reading it could end the process. */
internal const val MAX_PICTURE_BYTES = 20L * 1024 * 1024

/** Copies this stream into [to] and stops at [cap] bytes. False when there was more, so the copy is not the whole. */
internal fun InputStream.copyUpTo(cap: Long, to: OutputStream): Boolean {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) return true
        if (copied + read > cap) return false
        to.write(buffer, 0, read)
        copied += read
    }
}

/**
 * FR-1: the pictures the share sheet handed over, waiting for the next import run.
 *
 * The bytes are copied in at once rather than the URI being kept. An `ACTION_SEND` grant lives
 * only as long as the Activity that received it, and cannot be made persistable the way a document
 * picker's can — but the import runs later, in a worker, in a process that may have restarted
 * since. A copy is the only thing that survives that gap.
 *
 * It is a [SlipSource] like the folder scan and not a path of its own, which is what makes US-4
 * true: a slip shared by hand and the same slip found by the scan are two candidates with the same
 * bytes, so the importer's hash sees the second one for what it is.
 */
class SharedSlipInbox(context: Context) : SlipSource {

    private val context = context.applicationContext
    private val inbox = File(this.context.filesDir, INBOX_FOLDER)

    /**
     * Copies one shared picture in. Throws with a line written for the reader, because the share
     * sheet has already closed by the time this runs and a toast is all that is left to say it.
     */
    suspend fun accept(picture: Uri) = withContext(Dispatchers.IO) {
        // A provider's picture only: a file path could name one of this app's own files.
        if (picture.scheme != ContentResolver.SCHEME_CONTENT) throw IOException(AppText.copy.slips.shareNotOpened)

        // Copied beside the inbox and moved in whole, so a run never reads half a picture.
        val staged = File(context.filesDir, "$INBOX_FOLDER-${UUID.randomUUID()}")
        try {
            val whole = try {
                context.contentResolver.openInputStream(picture)?.use { shared -> staged.outputStream().use { shared.copyUpTo(MAX_PICTURE_BYTES, it) } }
            } catch (unreadable: IOException) {
                throw IOException(AppText.copy.slips.shareNotOpened, unreadable)
            } catch (refused: SecurityException) {
                throw SecurityException(AppText.copy.slips.shareRefused, refused)
            }

            if (whole == null || staged.length() == 0L) throw IOException(AppText.copy.slips.shareEmpty)
            // No slip is this large, and the importer holds each picture in memory to read it.
            if (!whole) throw IOException(AppText.copy.slips.shareNotOpened)

            inbox.mkdirs()
            if (!staged.renameTo(File(inbox, UUID.randomUUID().toString()))) throw IOException(AppText.copy.slips.shareNotOpened)
        } finally {
            staged.delete()
        }
    }

    override suspend fun candidates(): List<SlipCandidate> = withContext(Dispatchers.IO) {
        inbox.listFiles().orEmpty()
            .map { SlipCandidate(id = it.name, capturedAtEpochMillis = it.lastModified()) }
            .sortedBy { it.capturedAtEpochMillis }
    }

    override suspend fun bytesOf(id: String): ByteArray? = withContext(Dispatchers.IO) {
        slipAt(id)?.takeIf { it.isFile }?.readBytes()
    }

    override suspend fun delete(id: String) {
        withContext(Dispatchers.IO) { slipAt(id)?.delete() }
    }

    /**
     * Null for anything that is not a plain name inside the inbox. Ids come back through Room and
     * through the queued rows of older builds, so a separator here would be a way out of the folder.
     */
    private fun slipAt(id: String): File? =
        if (id.isEmpty() || File.separatorChar in id || id == "." || id == "..") null else File(inbox, id)
}
