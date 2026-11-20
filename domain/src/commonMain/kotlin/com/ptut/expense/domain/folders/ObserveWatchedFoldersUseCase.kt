package com.ptut.expense.domain.folders

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Every folder Photo folders lists, watched or not, as they change; none in a build without folders. */
class ObserveWatchedFoldersUseCase(private val repository: WatchedFolderRepository? = null) {
    operator fun invoke(): Flow<List<WatchedFolder>> = repository?.folders ?: flowOf(emptyList())
}
