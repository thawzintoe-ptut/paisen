package com.ptut.expense.domain.backup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** #220: where one restore stands, from a chosen file to the merge. Import my data and first run's RESTORE share it. */
sealed interface RestoreStep {
    data object Idle : RestoreStep

    /** The chosen file is being read and checked. Nothing is merged yet. */
    data object Reading : RestoreStep

    /** What the file holds, asked about before anything merges. */
    data class Preview(val holds: BackupPreview) : RestoreStep

    /** The file was refused, at the preview or at the merge; the ledger is as it was. */
    data class Refused(val reason: BackupProblem) : RestoreStep

    data object Importing : RestoreStep

    data class Done(val report: MergeReport) : RestoreStep
}

/**
 * Read, preview, then import or cancel, over one [LedgerArchive]. The picker stays with each screen,
 * so backing out of it never reaches here. A failure that is not a refusal goes back to
 * [RestoreStep.Idle] and is thrown for the screen to word.
 */
class RestoreDriver(private val archive: LedgerArchive) {

    private val current = MutableStateFlow<RestoreStep>(RestoreStep.Idle)

    val step: StateFlow<RestoreStep> = current.asStateFlow()

    private var chosen: String? = null

    /** The text a picker returned: previewed, or refused. */
    suspend fun read(text: String) {
        chosen = null
        current.value = RestoreStep.Reading
        current.value = refusing {
            val holds = archive.preview(text)
            chosen = text
            RestoreStep.Preview(holds)
        }
    }

    /**
     * Merges the previewed file; [around] wraps the merge for a screen that does work beside it.
     * Null when no file waits, or when the archive refused it.
     */
    suspend fun import(around: suspend (merge: suspend () -> MergeReport) -> MergeReport = { merge -> merge() }): MergeReport? {
        val text = chosen ?: return null
        chosen = null
        current.value = RestoreStep.Importing
        val done = refusing { RestoreStep.Done(around { archive.restore(text) }) }
        current.value = done
        return (done as? RestoreStep.Done)?.report
    }

    /** Cancel on the preview, or a failure the screen has worded: the file is let go and nothing has changed. */
    fun cancel() {
        chosen = null
        current.value = RestoreStep.Idle
    }

    private inline fun refusing(work: () -> RestoreStep): RestoreStep = try {
        work()
    } catch (refusal: BackupUnreadable) {
        RestoreStep.Refused(refusal.problem)
    } catch (failure: Throwable) {
        cancel()
        throw failure
    }
}
