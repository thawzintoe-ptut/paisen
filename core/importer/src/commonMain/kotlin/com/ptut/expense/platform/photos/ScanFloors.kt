package com.ptut.expense.platform.photos

import com.ptut.expense.domain.folders.WatchedFolderRepository
import kotlin.time.Clock

internal const val MILLIS_PER_SECOND = 1_000L

/**
 * How many pictures one run may read, across every folder together.
 *
 * A folder added later is read from its own beginning, and Screenshots can hold thousands. Without
 * a ceiling that first run does not finish inside the fifteen minutes WorkManager allows it, the
 * floor never advances, and the next run starts the same work again — a scan that burns the battery
 * every quarter of an hour and never gets anywhere. With one, every run finishes and every run
 * leaves the floor higher than it found it.
 */
internal const val BATCH_CAP = 200

/**
 * The per-folder floors a photo scan hands out, shared by the Android and iOS sources.
 *
 * A run lists each folder through its newest picture and writes nothing until the importer has
 * gone through the whole batch. A read that fails holds its folder below that picture, so the next
 * scan lists it again instead of stepping over it for good.
 */
internal class ScanFloors(
    private val folders: WatchedFolderRepository,
    private val clock: Clock,
) {

    /** Added-at second per candidate, so a read that fails can hold its folder behind it. */
    private var batch = emptyMap<String, Long>()

    /** Which folder listed a candidate, so [holdBelow] knows whose floor to keep down. */
    private var folderOf = emptyMap<String, String>()

    /** Listed-through per folder, held until the run that listed it finishes its batch. */
    private var pending = mutableMapOf<String, Long>()

    /**
     * An even share rather than first-come: one folder mid-backfill would otherwise take every
     * run's budget, and the folders behind it would read "Not checked" for as long as it took.
     */
    fun shareFor(folderCount: Int): Int = maxOf(1, BATCH_CAP / folderCount)

    /** Drops floors a stopped run never settled, so its batch lists again and the hash journal skips what landed. */
    fun startListing() {
        pending = mutableMapOf()
    }

    /** Commits the floors of a run that went through its whole batch. A process that died mid-run never gets here. */
    suspend fun settle() {
        if (pending.isEmpty()) return
        val settling = pending
        pending = mutableMapOf()
        val at = clock.now().toEpochMilliseconds()
        settling.forEach { (folderId, through) -> folders.recordScan(folderId, through, at) }
    }

    /** A full share may have cut [newest]'s second part-way, so the floor stays one below it if that still moves it. */
    fun listedThrough(folderId: String, floor: Long, newest: Long, filledShare: Boolean) {
        pending[folderId] = if (filledShare && newest - 1 > floor) newest - 1 else newest
    }

    /** The candidates this run handed out: their second and the folder that listed each. */
    fun handOut(secondsOf: Map<String, Long>, folderOf: Map<String, String>) {
        batch = secondsOf
        this.folderOf = folderOf
    }

    fun holdBelow(id: String) {
        val folderId = folderOf[id] ?: return
        val seconds = batch[id] ?: return
        val listed = pending[folderId] ?: return
        pending[folderId] = minOf(listed, seconds - 1)
    }
}
