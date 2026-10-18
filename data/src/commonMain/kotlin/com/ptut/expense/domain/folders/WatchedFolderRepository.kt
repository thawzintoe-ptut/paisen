package com.ptut.expense.domain.folders

import kotlinx.coroutines.flow.StateFlow

/**
 * The folders the importer reads, and the only thing that may change that set.
 *
 * The scan and the screen both write here — the scan records what it read, the screen adds and
 * removes rows — so both go through one owner rather than each holding its own copy.
 */
interface WatchedFolderRepository : FolderReadTally {

    val folders: StateFlow<List<WatchedFolder>>

    suspend fun watch(id: String, name: String, path: String)

    suspend fun stopWatching(id: String)

    /**
     * What the table holds right now, watched or not. [folders] is a shared flow that has not
     * necessarily emitted when the app is still starting, and seeding off an empty first value
     * would hand a reader back the folder they deliberately switched off.
     */
    suspend fun known(): List<WatchedFolder>

    /** This folder's own floor, and zero for one that has never been scanned. */
    suspend fun scannedThroughSeconds(id: String): Long

    /**
     * One scan of one folder, reported by whatever did the listing: how far it got, and that it
     * looked at all. Recorded even when nothing was found — that is what turns "Not checked" into
     * "No slips found yet", which is the distinction the screen exists to make.
     */
    suspend fun recordScan(id: String, throughSeconds: Long, checkedAtEpochMillis: Long)
}
