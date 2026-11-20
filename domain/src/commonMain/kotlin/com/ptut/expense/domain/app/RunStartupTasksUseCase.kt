package com.ptut.expense.domain.app

import com.ptut.expense.domain.folders.FirstRunFolders
import com.ptut.expense.importer.SlipImporter
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * What the shell starts with, neither waiting on the other: NFR-4's sweep on every start, so old evidence goes even
 * without imports, and US-1's folders before the first scan, so it is not a healthy empty run. [firstRun] may be null.
 */
class RunStartupTasksUseCase(
    private val importer: SlipImporter,
    private val firstRun: FirstRunFolders? = null,
) {
    suspend operator fun invoke() {
        supervisorScope {
            launch { importer.sweep() }
            launch { firstRun?.seed() }
        }
    }
}
