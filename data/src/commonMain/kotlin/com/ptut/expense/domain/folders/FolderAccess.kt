package com.ptut.expense.domain.folders

/**
 * What the reader can still be offered when the folder they want is not in the list.
 *
 * The two systems answer this differently and the copy has to follow, so the wording sits beside
 * the platform that has to be true to it — the argument
 * [com.ptut.expense.settings.SlipImportCadence] already makes for the Settings rows.
 *
 * On Android with full photo access every folder holding an image is already listed, so there is
 * nothing left to choose and [action] is null. On iOS the reader granted access album by album,
 * so there is always more to share.
 */
data class FolderAccess(
    /** The label on the call to action, or null where the list is already complete. */
    val action: String?,
    val explanation: String,
)

/**
 * Opens whatever the platform offers for reaching more pictures. A no-op where [FolderAccess.action]
 * is null, because nothing is offered there.
 */
fun interface FolderChooser {

    suspend fun chooseMore()
}
