package com.ptut.expense.domain.folders

import com.ptut.expense.domain.runSuspendCatching

/**
 * What the platform can still offer beyond the list, read each time it is asked (#217): access
 * granted in the system settings has to show when the reader comes back, not after a restart.
 */
class GetFolderAccessUseCase(private val access: () -> FolderAccess = { FolderAccess(action = null, explanation = "") }) {
    operator fun invoke(): FolderAccess = access()
}

/** FR-5: every folder the platform offers right now. Empty when photo access was not granted. */
class RefreshFolderCatalogUseCase(private val catalog: FolderCatalog) {
    suspend operator fun invoke(): List<DeviceFolder> = catalog.available()
}

/**
 * FR-5's SAVE: the folders in [stop] are dropped, then the ones in [watch] are watched. One folder
 * failing does not stop the rest. True when every change landed.
 */
class ApplyWatchedFoldersUseCase(private val folders: WatchedFolderRepository) {
    suspend operator fun invoke(stop: List<String>, watch: List<DeviceFolder>): Boolean {
        val dropped = stop.map { id -> runSuspendCatching { folders.stopWatching(id) } }
        val added = watch.map { folder -> runSuspendCatching { folders.watch(folder.id, folder.name, folder.path) } }
        return (dropped + added).none { it.isFailure }
    }
}
