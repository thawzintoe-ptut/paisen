package com.ptut.expense.domain.folders

/**
 * One folder on the device that could hold slips, as the platform sees it.
 *
 * [id] has to survive a rename: MediaStore's bucket id on Android, the album's local identifier
 * on iOS. [path] is what the row prints in monospace, and it is the only part a reader recognises.
 */
data class DeviceFolder(
    val id: String,
    val name: String,
    val path: String,
    /** How many images the folder holds (#169, p22), or null where the platform did not count. */
    val imageCount: Int? = null,
)

/**
 * What the picker may offer. An interface and not an `expect`, so the screen's merge rules run in
 * the host suite against a fake — the same argument [com.ptut.expense.platform.photos.SlipSource]
 * makes.
 */
fun interface FolderCatalog {

    /** Empty when the reader has not granted photo access; the screen says so rather than guessing. */
    suspend fun available(): List<DeviceFolder>
}
