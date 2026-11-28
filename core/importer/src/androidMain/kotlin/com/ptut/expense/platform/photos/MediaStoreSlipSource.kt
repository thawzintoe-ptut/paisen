package com.ptut.expense.platform.photos

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.ptut.expense.domain.ImportFailureKind
import com.ptut.expense.domain.folders.WatchedFolderRepository
import com.ptut.expense.domain.folders.watched
import com.ptut.expense.importer.ScanStopped
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.time.Clock

/** DATE_ADDED is seconds, DATE_TAKEN is milliseconds, and only one of them is ever set. */
private val PROJECTION = arrayOf(
    MediaStore.Images.Media._ID,
    MediaStore.Images.Media.DATE_ADDED,
    MediaStore.Images.Media.DATE_TAKEN,
)

private const val OLDEST_FIRST = "${MediaStore.Images.Media.DATE_ADDED} ASC"

/** Under SQLite's oldest limit of 999 bound values per query. */
private const val IDS_PER_QUERY = 500

private const val PICKS_FILE = "picked_photos"
private const val FINISHED = "finished"

/**
 * Thrown rather than answered with an empty list. US-8 counts a scan that found no slips as a
 * success, so a silent empty answer would leave the banner green while nothing was being watched.
 * The importer journals this message, so it is written for the person reading it.
 */
class PhotoAccessDenied : ScanStopped(ImportFailureKind.PhotoAccessDenied, AppText.copy.folders.androidAccessDenied)

/**
 * FR-2 and FR-5: every folder the reader watches, read through MediaStore.
 *
 * Each folder carries its own floor. One floor for the whole scan was right while there was one
 * folder — a folder added afterwards has every picture already in it sitting below a shared floor,
 * so it would be watched and never read, and nothing on screen would say why.
 *
 * [delete] removes nothing. Scoped storage will not let this app remove a picture a banking app
 * wrote, and below Android 10 it holds no write permission either, so the gallery photo always
 * stays and the app says so (#219). NFR-4's promise holds for the copies this app makes and for
 * anything it owns. A slip that survives is recognised by its hash on the next scan and never
 * read twice.
 *
 * Under Android 14's "Select photos" the floors stay put. A photo picked later can be older than
 * one already read, and a floor raised over the picks would hide every unpicked slip below it once
 * the reader allows all photos. So each pick carries its own floor instead: once a run has gone
 * through it, [settle] marks it finished with the floors, and a read that failed holds it back.
 * [delete] marks one at once, so a discard holds even when the process dies before the next scan.
 */
class MediaStoreSlipSource(
    context: Context,
    private val folders: WatchedFolderRepository,
    private val clock: Clock = Clock.System,
) : SlipSource {

    private val context = context.applicationContext

    /** Floors in `DATE_ADDED` seconds, so a read that fails can hold its folder behind that row. */
    private val floors = ScanFloors(folders, clock)

    /** Picks listed by this run, finished when the run settles them. */
    private var pendingPicks = mutableSetOf<String>()

    /** Picks no scan reads again. Android only, so it lives here and not in the ledger. */
    private val finishedPicks by lazy { context.getSharedPreferences(PICKS_FILE, Context.MODE_PRIVATE) }

    override suspend fun candidates(): List<SlipCandidate> = withContext(Dispatchers.IO) {
        val level = context.photoAccessLevel()
        if (level == PhotoAccessLevel.None) throw PhotoAccessDenied()

        pendingPicks = mutableSetOf()
        floors.startListing()

        val watched = folders.folders.value.watched()
        if (watched.isEmpty()) return@withContext emptyList()

        val share = floors.shareFor(watched.size)
        val listedAt = clock.now().toEpochMilliseconds()

        val found = mutableListOf<SlipCandidate>()
        val addedAt = mutableMapOf<String, Long>()
        val owner = mutableMapOf<String, String>()
        // Skipped at every level: floors left low by "Select photos" would list these again after "Allow all".
        val finished = if (level == PhotoAccessLevel.All) forgetDeletedPicks() else finishedPicks()

        for (folder in watched) {
            val floor = folders.scannedThroughSeconds(folder.id)
            val rows = queryFolder(folder.id, after = floor, cap = share, skip = finished)

            rows.forEach { row ->
                val id = row.id.toString()
                addedAt[id] = row.addedAtSeconds
                owner[id] = folder.id
                found += SlipCandidate(id, row.capturedAtEpochMillis, folder.id, folder.name)
            }
            if (level == PhotoAccessLevel.All) {
                rows.maxOfOrNull { it.addedAtSeconds }?.let { floors.listedThrough(folder.id, floor, it, filledShare = rows.size >= share) }
            }

            // Stamped as looked-at now, at the floor it already had. That is what tells a folder
            // holding nothing apart from one nothing has ever looked in.
            folders.recordScan(folder.id, floor, listedAt)
        }

        floors.handOut(addedAt, owner)
        if (level == PhotoAccessLevel.Picked) pendingPicks = addedAt.keys.toMutableSet()
        // Oldest first across the whole run, so a run that stops early has still made progress
        // in order rather than part-way through each folder at once.
        found.sortedBy { it.capturedAtEpochMillis }
    }

    override suspend fun bytesOf(id: String): ByteArray? = withContext(Dispatchers.IO) {
        val picture = pictureAt(id) ?: return@withContext null
        try {
            context.contentResolver.openInputStream(picture)?.use { stream ->
                val whole = ByteArrayOutputStream()
                // Too large to be a slip, and a retry will not shrink it, so the floor is not held.
                if (stream.copyUpTo(MAX_PICTURE_BYTES, whole)) whole.toByteArray() else null
            }
        } catch (gone: FileNotFoundException) {
            // The reader deleted it between the scan and now. The row goes with it, so there is
            // nothing to come back for.
            null
        } catch (unpicked: SecurityException) {
            // Under "Select photos" the reader can untick a picture after it was read. For this app
            // it is as gone as a deleted one, and To check shows the slip without it.
            null
        } catch (unreadable: IOException) {
            // Still there, just not readable this minute. Holding the floor is what turns that
            // into a retry on the next scan instead of a slip stepped over for good.
            holdBelow(id)
            null
        }
    }

    /** See the class note: the gallery photo is the reader's, and it stays. */
    override suspend fun delete(id: String) {
        if (context.photoAccessLevel() == PhotoAccessLevel.Picked) finish(setOf(id))
    }

    private fun finish(ids: Set<String>) = synchronized(this) {
        finishedPicks.edit().putStringSet(FINISHED, finishedPicks() + ids).apply()
    }

    /**
     * Under "Allow all" every row is visible, so a finished pick MediaStore no longer lists was
     * deleted and has nothing left to skip. Under "Select photos" an unticked pick also vanishes, so the set stays.
     */
    private fun forgetDeletedPicks(): Set<String> {
        val finished = finishedPicks()
        if (finished.isEmpty()) return finished
        val gone = finished - finished.chunked(IDS_PER_QUERY).flatMapTo(mutableSetOf(), ::stillListed)
        if (gone.isEmpty()) return finished
        synchronized(this) {
            finishedPicks.edit().putStringSet(FINISHED, finishedPicks() - gone).apply()
        }
        return finished - gone
    }

    /** The [ids] MediaStore still lists. No answer keeps them all, since a wrong prune lists a slip twice. */
    private fun stillListed(ids: List<String>): Set<String> {
        val cursor = context.contentResolver.query(
            imageCollection,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media._ID} IN (${ids.joinToString(",") { "?" }})",
            ids.toTypedArray(),
            null,
        ) ?: return ids.toSet()
        return cursor.use { rows ->
            val id = rows.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            buildSet { while (rows.moveToNext()) add(rows.getLong(id).toString()) }
        }
    }

    /** A copy: the set SharedPreferences hands out must not be changed in place. */
    private fun finishedPicks(): Set<String> = finishedPicks.getStringSet(FINISHED, null)?.toSet().orEmpty()

    /** Picks settle with the floors, once the run has gone through every one it listed. */
    override suspend fun settle() {
        if (pendingPicks.isNotEmpty()) {
            finish(pendingPicks)
            pendingPicks = mutableSetOf()
        }
        floors.settle()
    }

    override suspend fun retryLater(id: String) = holdBelow(id)

    private fun holdBelow(id: String) {
        pendingPicks.remove(id)
        floors.holdBelow(id)
    }

    /**
     * Below Android 10 a folder is a directory and the column holds a whole file path, so the
     * match is a prefix and not an equality. Above it the bucket is a value in its own right.
     */
    private fun queryFolder(folderId: String, after: Long, cap: Int, skip: Set<String>): List<Row> {
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val match = if (modern) "$folderColumn = ?" else "$folderColumn LIKE ?"
        val wanted = if (modern) folderId else "$folderId/%"

        val cursor = context.contentResolver.query(
            imageCollection,
            PROJECTION,
            "$match AND ${MediaStore.Images.Media.DATE_ADDED} > ?",
            arrayOf(wanted, after.toString()),
            OLDEST_FIRST,
        ) ?: throw ScanStopped(ImportFailureKind.LibraryNoAnswer, AppText.copy.folders.libraryNoAnswer)

        return cursor.use { it.readRows(cap, skip) }
    }

    /** Stops at [cap] rather than reading the whole folder and dropping the tail; [skip] does not count. */
    private fun Cursor.readRows(cap: Int, skip: Set<String>): List<Row> {
        val id = getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val addedAt = getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val takenAt = getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

        return buildList {
            while (size < cap && moveToNext()) {
                val row = Row(getLong(id), getLong(addedAt), getLong(takenAt))
                if (row.id.toString() !in skip) add(row)
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
