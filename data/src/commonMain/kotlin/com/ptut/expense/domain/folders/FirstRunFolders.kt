package com.ptut.expense.domain.folders

/**
 * Names a banking app's own folder is likely to carry. Assumption 1 in the spec, still unverified,
 * which is why this matches loosely and in one place.
 *
 * Only used to pick a folder on a first run. Everything after that is the reader's choice, so a
 * miss here costs one switch on the Photo folders screen rather than an import that never happens.
 */
private val LIKELY_SLIP_FOLDERS = listOf("K PLUS", "KPLUS")

/**
 * US-1's promise on a fresh install: pay as usual, and the ledger fills itself. A reader who has
 * never opened Settings still has to be importing, so the folder every Thai K PLUS user has is
 * switched on without being asked.
 *
 * It runs once and never again — [WatchedFolderRepository.known] is non-empty from the first seed
 * onwards, and a row survives being switched off, so a reader who turns everything off is not
 * handed K PLUS back on the next launch.
 */
class FirstRunFolders(
    private val catalog: FolderCatalog,
    private val folders: WatchedFolderRepository,
) {

    suspend fun seed() {
        if (folders.known().isNotEmpty()) return

        catalog.available()
            .filter { it.name.looksLikeASlipFolder() }
            .forEach { folders.watch(it.id, it.name, it.path) }
    }

    private fun String.looksLikeASlipFolder(): Boolean =
        LIKELY_SLIP_FOLDERS.any { contains(it, ignoreCase = true) }
}
