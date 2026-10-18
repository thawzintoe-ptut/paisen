package com.ptut.expense.platform.photos

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.ptut.expense.domain.folders.WatchedFolderRepository
import com.ptut.expense.domain.folders.watched
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.time.Clock

private const val MILLIS_PER_SECOND = 1_000L

/**
 * How many pictures one run may read, across every folder together.
 *
 * A folder added later is read from its own beginning, and Screenshots can hold thousands. Without
 * a ceiling that first run does not finish inside the fifteen minutes WorkManager allows it, the
 * floor never advances, and the next run starts the same work again — a scan that burns the battery
 * every quarter of an hour and never gets anywhere. With one, every run finishes and every run
 * leaves the floor higher than it found it.
 */
private const val BATCH_CAP = 200

/** DATE_ADDED is seconds, DATE_TAKEN is milliseconds, and only one of them is ever set. */
private val PROJECTION = arrayOf(
    MediaStore.Images.Media._ID,
    MediaStore.Images.Media.DATE_ADDED,
    MediaStore.Images.Media.DATE_TAKEN,
)

private const val OLDEST_FIRST = "${MediaStore.Images.Media.DATE_ADDED} ASC"

/**
 * Thrown rather than answered with an empty list. US-8 counts a scan that found no slips as a
 * success, so a silent empty answer would leave the banner green while nothing was being watched.
 * The importer journals this message, so it is written for the person reading it.
 */
class PhotoAccessDenied : IllegalStateException(AppText.copy.folders.androidAccessDenied)

/**
 * FR-2 and FR-5: every folder the reader watches, read through MediaStore.
 *
 * Each folder carries its own floor. One floor for the whole scan was right while there was one
 * folder — a folder added afterwards has every picture already in it sitting below a shared floor,
 * so it would be watched and never read, and nothing on screen would say why.
 *
 * [delete] does nothing. Scoped storage will not let this app remove a picture a banking app
 * wrote, and below Android 10 it holds no write permission either, so the gallery photo always
 * stays and the app says so (#219). NFR-4's promise holds for the copies this app makes and for
 * anything it owns. A slip that survives is recognised by its hash on the next scan and never
 * read twice.
 */
class MediaStoreSlipSource(
    context: Context,
    private val folders: WatchedFolderRepository,
    private val clock: Clock = Clock.System,
) : SlipSource {

    private val context = context.applicationContext

    /** `DATE_ADDED` per candidate, so a read that fails can hold its folder behind that row. */
    private var batch = emptyMap<String, Long>()

    /** Which folder listed a candidate, so [holdBelow] knows whose floor to keep down. */
    private var folderOf = emptyMap<String, String>()

    /** Listed-through per folder, held until the next scan settles it. */
    private var pending = mutableMapOf<String, Long>()

    override suspend fun candidates(): List<SlipCandidate> = withContext(Dispatchers.IO) {
        if (!context.hasPhotoAccess()) throw PhotoAccessDenied()

        settlePreviousBatch()

        val watched = folders.folders.value.watched()
        if (watched.isEmpty()) return@withContext emptyList()

        // An even share rather than first-come: one folder mid-backfill would otherwise take every
        // run's budget, and the folders behind it would read "Not checked" for as long as it took.
        val share = maxOf(1, BATCH_CAP / watched.size)
        val listedAt = clock.now().toEpochMilliseconds()

        val found = mutableListOf<SlipCandidate>()
        val addedAt = mutableMapOf<String, Long>()
        val owner = mutableMapOf<String, String>()

        for (folder in watched) {
            val floor = folders.scannedThroughSeconds(folder.id)
            val rows = queryFolder(folder.id, after = floor, cap = share)

            rows.forEach { row ->
                val id = row.id.toString()
                addedAt[id] = row.addedAtSeconds
                owner[id] = folder.id
                found += SlipCandidate(id, row.capturedAtEpochMillis, folder.id, folder.name)
            }
            rows.maxOfOrNull { it.addedAtSeconds }?.let { pending[folder.id] = it }

            // Stamped as looked-at now, at the floor it already had. That is what tells a folder
            // holding nothing apart from one nothing has ever looked in.
            folders.recordScan(folder.id, floor, listedAt)
        }

        batch = addedAt
        folderOf = owner
        // Oldest first across the whole run, so a run that stops early has still made progress
        // in order rather than part-way through each folder at once.
        found.sortedBy { it.capturedAtEpochMillis }
    }

    override suspend fun bytesOf(id: String): ByteArray? = withContext(Dispatchers.IO) {
        val picture = pictureAt(id) ?: return@withContext null
        try {
            context.contentResolver.openInputStream(picture)?.use { it.readBytes() }
        } catch (gone: FileNotFoundException) {
            // The reader deleted it between the scan and now. The row goes with it, so there is
            // nothing to come back for.
            null
        } catch (unreadable: IOException) {
            // Still there, just not readable this minute. Holding the floor is what turns that
            // into a retry on the next scan instead of a slip stepped over for good.
            holdBelow(id)
            null
        }
    }

    /** See the class note: the gallery photo is the reader's, and it stays. */
    override suspend fun delete(id: String) = Unit

    /**
     * Commits the floors handed out by the previous run. Nothing is written while a batch is in
     * flight, so a process that died mid-run lists the same batch again — bounded by [BATCH_CAP] —
     * and the hash journal recognises whatever did land the first time.
     */
    private suspend fun settlePreviousBatch() {
        if (pending.isEmpty()) return
        val settling = pending
        pending = mutableMapOf()
        val at = clock.now().toEpochMilliseconds()
        settling.forEach { (folderId, through) -> folders.recordScan(folderId, through, at) }
    }

    private fun holdBelow(id: String) {
        val folderId = folderOf[id] ?: return
        val seconds = batch[id] ?: return
        val listed = pending[folderId] ?: return
        pending[folderId] = minOf(listed, seconds - 1)
    }

    /**
     * Below Android 10 a folder is a directory and the column holds a whole file path, so the
     * match is a prefix and not an equality. Above it the bucket is a value in its own right.
     */
    private fun queryFolder(folderId: String, after: Long, cap: Int): List<Row> {
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val match = if (modern) "$folderColumn = ?" else "$folderColumn LIKE ?"
        val wanted = if (modern) folderId else "$folderId/%"

        val cursor = context.contentResolver.query(
            imageCollection,
            PROJECTION,
            "$match AND ${MediaStore.Images.Media.DATE_ADDED} > ?",
            arrayOf(wanted, after.toString()),
            OLDEST_FIRST,
        ) ?: throw IllegalStateException(AppText.copy.folders.libraryNoAnswer)

        return cursor.use { it.readRows(cap) }
    }

    /** Stops at [cap] rather than reading the whole folder and dropping the tail. */
    private fun Cursor.readRows(cap: Int): List<Row> {
        val id = getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val addedAt = getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val takenAt = getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

        return buildList {
            while (size < cap && moveToNext()) {
                add(Row(getLong(id), getLong(addedAt), getLong(takenAt)))
            }
        }
    }

    /** Null for an id that is not a MediaStore row — one left over from a build wired differently. */
    private fun pictureAt(id: String): Uri? =
        id.toLongOrNull()?.let { ContentUris.withAppendedId(imageCollection, it) }

    private data class Row(val id: Long, val addedAtSeconds: Long, val takenAtMillis: Long) {

        /**
         * NFR-4 sweeps a failed slip seven days after the picture was taken, so the age has to be
         * the picture's. A slip is a screenshot and screenshots carry no EXIF date, which leaves
         * the moment MediaStore first saw the file as the closest honest stand-in.
         */
        val capturedAtEpochMillis: Long
            get() = if (takenAtMillis > 0) takenAtMillis else addedAtSeconds * MILLIS_PER_SECOND
    }
}

/**
 * A watched folder is identified by its bucket, so the scan asks for that and not for a name.
 * `BUCKET_ID` only exists from Android 10; below it the folder is the parent directory of the
 * absolute path, deprecated since — but this app still runs on Android 8 (NFR-10), and
 * [MediaStoreFolderCatalog] hands out the same two identities.
 */
@Suppress("DEPRECATION")
private val folderColumn: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.BUCKET_ID
    } else {
        MediaStore.Images.Media.DATA
    }

internal val imageCollection: Uri
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
