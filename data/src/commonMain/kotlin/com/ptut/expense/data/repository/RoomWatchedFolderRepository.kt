package com.ptut.expense.data.repository

import com.ptut.expense.data.local.dao.WatchedFolderDao
import com.ptut.expense.data.local.entity.WatchedFolderEntity
import com.ptut.expense.data.local.entity.toDomain
import com.ptut.expense.domain.folders.WatchedFolder
import com.ptut.expense.domain.folders.WatchedFolderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * [WatchedFolderRepository] over Room. Mapping only, with one rule of its own: watching a folder
 * again must not reset what it has already read, so [watch] upserts the name and path and leaves
 * the counter and the floor where the scan left them.
 */
class RoomWatchedFolderRepository(
    private val dao: WatchedFolderDao,
    scope: CoroutineScope,
) : WatchedFolderRepository {

    override val folders: StateFlow<List<WatchedFolder>> = dao.observeAll()
        .map { rows -> rows.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun watch(id: String, name: String, path: String) {
        val held = dao.all().firstOrNull { it.id == id }
        dao.upsert(
            WatchedFolderEntity(
                id = id,
                name = name,
                path = path,
                watched = true,
                // A folder taken off the list and put back keeps its history. The pictures did not
                // go anywhere, and re-reading them would double every count the badge is based on.
                slipsRead = held?.slipsRead ?: 0,
                scannedThroughSeconds = held?.scannedThroughSeconds ?: 0L,
                lastCheckedAtEpochMillis = held?.lastCheckedAtEpochMillis,
            ),
        )
    }

    override suspend fun stopWatching(id: String) = dao.setWatched(id, watched = false)

    override suspend fun known(): List<WatchedFolder> = dao.all().map { it.toDomain() }

    override suspend fun scannedThroughSeconds(id: String): Long =
        dao.all().firstOrNull { it.id == id }?.scannedThroughSeconds ?: 0L

    override suspend fun recordScan(id: String, throughSeconds: Long, checkedAtEpochMillis: Long) =
        dao.recordCheck(id, throughSeconds, checkedAtEpochMillis)

    override suspend fun addSlipsRead(id: String, read: Int) {
        if (read > 0) dao.addSlipsRead(id, read)
    }
}
