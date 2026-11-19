package com.ptut.expense.domain.folders

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Watched folders in memory. Switching one off keeps its row and its count, as the real
 * repository does, and each folder's floor is what the last [recordScan] gave it.
 */
class FakeFolders(seed: List<WatchedFolder> = emptyList()) : WatchedFolderRepository {

    override val folders = MutableStateFlow(seed)

    private val floors = mutableMapOf<String, Long>()

    override suspend fun watch(id: String, name: String, path: String) {
        val held = folders.value.firstOrNull { it.id == id }
        folders.value = folders.value.filterNot { it.id == id } +
            (held?.copy(watched = true) ?: WatchedFolder(id, name, path, slipsRead = 0, checked = false))
    }

    override suspend fun stopWatching(id: String) {
        folders.value = folders.value.map { if (it.id == id) it.copy(watched = false) else it }
    }

    override suspend fun known(): List<WatchedFolder> = folders.value

    override suspend fun scannedThroughSeconds(id: String): Long = floors[id] ?: 0L

    override suspend fun recordScan(id: String, throughSeconds: Long, checkedAtEpochMillis: Long) {
        floors[id] = throughSeconds
    }

    override suspend fun addSlipsRead(id: String, read: Int) = Unit
}
