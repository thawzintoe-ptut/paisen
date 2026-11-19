package com.ptut.expense.platform.photos

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import com.ptut.expense.domain.folders.DeviceFolder
import com.ptut.expense.domain.folders.FolderCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every folder on the phone that holds an image, as MediaStore buckets.
 *
 * The bucket id is the identity and not the name: a folder renamed in a gallery app keeps its
 * bucket, and a watched folder that changed its label has to keep the count it has already earned.
 *
 * `BUCKET_ID` and `RELATIVE_PATH` only exist from Android 10. Below that the folder is only
 * reachable through the absolute path, deprecated since — but this app still runs on Android 8
 * (NFR-10), so the fallback derives both from the parent directory of `DATA`.
 */
class MediaStoreFolderCatalog(context: Context) : FolderCatalog {

    private val context = context.applicationContext

    override suspend fun available(): List<DeviceFolder> = withContext(Dispatchers.IO) {
        // Under "Select photos" MediaStore answers with the picks alone, so the list is their folders.
        if (context.photoAccessLevel() == PhotoAccessLevel.None) return@withContext emptyList()

        val cursor = context.contentResolver.query(
            imageCollection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        ) ?: return@withContext emptyList()

        // One row per image, so the folders are collected here rather than asked for with a
        // DISTINCT the older platform does not answer the same way.
        cursor.use { rows ->
            val readFolder = rows.folderReader()
            val found = LinkedHashMap<String, DeviceFolder>()
            // Counted on the same walk: one row per image is already the count (#169).
            val images = HashMap<String, Int>()
            while (rows.moveToNext()) {
                val folder = readFolder() ?: continue
                found.getOrPut(folder.id) { folder }
                images[folder.id] = (images[folder.id] ?: 0) + 1
            }
            found.values
                .map { it.copy(imageCount = images[it.id]) }
                .sortedBy { it.name.lowercase() }
        }
    }

    /** Looks the columns up once, not once per image. */
    private fun Cursor.folderReader(): () -> DeviceFolder? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) modernFolderReader() else legacyFolderReader()

    private fun Cursor.modernFolderReader(): () -> DeviceFolder? {
        val bucket = getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
        val bucketName = getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val relativePath = getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
        return reader@{
            val id = getString(bucket) ?: return@reader null
            val name = getString(bucketName) ?: return@reader null
            val path = getString(relativePath).orEmpty()
            DeviceFolder(id = id, name = name, path = path.trimEnd('/').ifEmpty { name })
        }
    }

    @Suppress("DEPRECATION")
    private fun Cursor.legacyFolderReader(): () -> DeviceFolder? {
        val data = getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        return reader@{
            val file = getString(data) ?: return@reader null
            val directory = file.substringBeforeLast('/', missingDelimiterValue = "")
            if (directory.isEmpty()) return@reader null
            DeviceFolder(
                id = directory,
                name = directory.substringAfterLast('/'),
                path = directory.substringAfter("/0/", missingDelimiterValue = directory),
            )
        }
    }

    @Suppress("DEPRECATION")
    private val projection: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATE_ADDED,
            )
        } else {
            arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_ADDED)
        }
}
