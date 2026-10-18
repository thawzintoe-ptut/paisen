package com.ptut.expense.platform.photos

import android.content.Context
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
        if (!context.hasPhotoAccess()) return@withContext emptyList()

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
            val found = LinkedHashMap<String, DeviceFolder>()
            // Counted on the same walk: one row per image is already the count (#169).
            val images = HashMap<String, Int>()
            while (rows.moveToNext()) {
                val folder = rows.folder() ?: continue
                found.getOrPut(folder.id) { folder }
                images[folder.id] = (images[folder.id] ?: 0) + 1
            }
            found.values
                .map { it.copy(imageCount = images[it.id]) }
                .sortedBy { it.name.lowercase() }
        }
    }

    private fun android.database.Cursor.folder(): DeviceFolder? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) modernFolder() else legacyFolder()

    private fun android.database.Cursor.modernFolder(): DeviceFolder? {
        val id = getString(getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)) ?: return null
        val name = getString(getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
            ?: return null
        val path = getString(getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)).orEmpty()
        return DeviceFolder(id = id, name = name, path = path.trimEnd('/').ifEmpty { name })
    }

    @Suppress("DEPRECATION")
    private fun android.database.Cursor.legacyFolder(): DeviceFolder? {
        val file = getString(getColumnIndexOrThrow(MediaStore.Images.Media.DATA)) ?: return null
        val directory = file.substringBeforeLast('/', missingDelimiterValue = "")
        if (directory.isEmpty()) return null
        return DeviceFolder(
            id = directory,
            name = directory.substringAfterLast('/'),
            path = directory.substringAfter("/0/", missingDelimiterValue = directory),
        )
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
