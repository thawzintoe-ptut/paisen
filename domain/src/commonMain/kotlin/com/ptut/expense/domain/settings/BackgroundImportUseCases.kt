package com.ptut.expense.domain.settings

import com.ptut.expense.domain.ImportOutcome
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.platform.work.BackgroundImport
import kotlinx.coroutines.CancellationException

/**
 * FR-3: the choice is written before the schedule is touched, or a cancel against a failed save comes back at the next
 * launch. [background] is null where nothing is scheduled (C-4).
 */
class SetBackgroundImportUseCase(
    private val settings: AppSettings,
    private val background: BackgroundImport? = null,
) {
    operator fun invoke(running: Boolean) {
        settings.setBackgroundImport(running)
        if (running) background?.start() else background?.stop()
    }
}

/** What a scan the reader asked for came to. */
sealed interface ImportNowResult {
    data class Found(val found: Int, val filed: Int, val queued: Int) : ImportNowResult

    /** A run that found nothing is not a failure. */
    data object NothingNew : ImportNowResult

    /** The run reached the journal and stopped short; a folder it cannot read is the commonest. */
    data object CouldNotFinish : ImportNowResult

    /** Past the importer's own journal: the ledger itself failed. */
    data object CouldNotRun : ImportNowResult
}

/** FR-2: one scan now, for a reader who came here because nothing arrived. */
class RunImportNowUseCase(private val importer: SlipImporter) {
    suspend operator fun invoke(): ImportNowResult = try {
        val summary = importer.run()
        when {
            summary.outcome != ImportOutcome.Success -> ImportNowResult.CouldNotFinish
            summary.found == 0 -> ImportNowResult.NothingNew
            else -> ImportNowResult.Found(summary.found, summary.filed, summary.queued)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        ImportNowResult.CouldNotRun
    }
}
