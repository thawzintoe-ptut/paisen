package com.ptut.expense.domain.folders

/**
 * What the importer says about a folder after a run: this many slips were read here.
 *
 * A seam of its own rather than the whole of [WatchedFolderRepository], because the importer must
 * not be able to change which folders are watched — it reports, it does not decide. The scan's
 * other half, how far the folder was listed, belongs to the source that did the listing.
 */
fun interface FolderReadTally {

    suspend fun addSlipsRead(id: String, read: Int)
}
