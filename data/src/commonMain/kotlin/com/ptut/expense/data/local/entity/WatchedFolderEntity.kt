package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ptut.expense.domain.folders.WatchedFolder

/**
 * A folder the importer watches, and everything the scan needs to remember about it.
 *
 * [scannedThroughSeconds] is this folder's own floor, not the run's. One scalar for the whole scan
 * was right while there was one folder: a folder added later has every photo already in it sitting
 * below a shared floor, so it would be watched and never read. The floor belongs to the folder.
 *
 * [lastCheckedAtEpochMillis] is null until a scan has actually looked. That null is the difference
 * between "No slips found yet" and "Not checked", which the design keeps apart deliberately.
 *
 * A row survives being switched off. [watched] and not the row's existence is what the scan reads,
 * so a folder turned off keeps the count it earned and gets it back when it is turned on again —
 * and a reader who turns everything off is not handed K PLUS again on the next launch, which is
 * what "the table is empty" would have meant.
 */
@Entity(tableName = "watched_folder")
data class WatchedFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    val watched: Boolean,
    val slipsRead: Int,
    val scannedThroughSeconds: Long,
    val lastCheckedAtEpochMillis: Long?,
)

fun WatchedFolderEntity.toDomain(): WatchedFolder = WatchedFolder(
    id = id,
    name = name,
    path = path,
    watched = watched,
    slipsRead = slipsRead,
    checked = lastCheckedAtEpochMillis != null,
)
