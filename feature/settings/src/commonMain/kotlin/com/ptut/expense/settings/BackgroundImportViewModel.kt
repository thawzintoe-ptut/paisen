package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.ImportOutcome
import com.ptut.expense.domain.elapsedPhrase
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.settings.ImportSettings
import com.ptut.expense.importer.ImportSummary
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.locale.AppText
import com.ptut.expense.platform.work.BackgroundImport
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BackgroundImportUiState(
    /** Whether the schedule is meant to be running. False only where the reader switched it off. */
    val running: Boolean,
    /** C-4. False on a platform with no periodic window, and the screen draws no switch at all. */
    val adjustable: Boolean,
    val explanation: String,
    /** "Every 15 min" or "On each app open" — the rhythm as a heading, not as half a row. */
    val rhythm: String,
    val lastRun: String,
    /** True while a scan the reader asked for is in flight, so the button cannot be tapped twice. */
    val checking: Boolean,
)

/**
 * FR-2 and FR-3: the schedule that reads slips while the app is closed, and the one switch over it.
 *
 * The switch writes the choice before it touches the platform. A schedule cancelled against a
 * preference that failed to save would come back on the next launch, and the reader would be left
 * turning off something that will not stay off.
 *
 * [background] is null where there is nothing to schedule. That is C-4 rather than a missing
 * binding: iOS reads the album when the app opens and has no job to start or stop.
 */
class BackgroundImportViewModel(
    ledger: LedgerRepository,
    private val settings: ImportSettings,
    private val importer: SlipImporter,
    private val toasts: ToastMessages,
    private val cadence: SlipImportCadence,
    private val background: BackgroundImport? = null,
) : ViewModel() {

    private val checking = MutableStateFlow(false)

    val uiState: StateFlow<BackgroundImportUiState> = combine(
        ledger.sinceLastImportMillis,
        settings.choices,
        checking,
    ) { since, choices, scanning ->
        state(since, choices.backgroundImport, scanning)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = state(
            since = null,
            running = settings.choices.value.backgroundImport,
            checking = false,
        ),
    )

    /**
     * Switching off cancels the job and leaves the app scanning on open, which is what the copy
     * on the screen promises. Switching on schedules and scans at once — a switch that changes
     * nothing visible for fifteen minutes reads as a switch that did not work.
     */
    fun setRunning(running: Boolean) {
        settings.setBackgroundImport(running)
        if (running) background?.start() else background?.stop()
    }

    /** The reader is here because nothing arrived, so the screen offers the run rather than a wait. */
    fun checkNow() {
        if (checking.value) return
        checking.value = true
        viewModelScope.launch {
            try {
                toasts.show(importer.run().toast())
            } catch (failure: Exception) {
                // The importer journals a scan it could not start, so anything reaching here is
                // past that — the reader is looking at the screen and is told rather than left
                // with a button that appeared to do nothing.
                toasts.show(
                    ToastMessage(
                        text = failure.message ?: AppText.copy.settings.scanCouldNotRun,
                        tone = ToastTone.Error,
                    ),
                )
            } finally {
                checking.value = false
            }
        }
    }

    private fun state(since: Long?, running: Boolean, checking: Boolean) = BackgroundImportUiState(
        running = running,
        adjustable = cadence.importIsAdjustable,
        explanation = cadence.importExplanation,
        rhythm = cadence.importRhythm,
        lastRun = since
            ?.let { AppText.copy.settings.lastRun(elapsedPhrase(it)) }
            ?: AppText.copy.settings.nothingImportedYet,
        checking = checking,
    )

    /**
     * A run that found nothing is not a failure, and saying "0 slips" makes it read as one. What
     * the reader wants to know is whether anything is there, so the empty case says exactly that.
     *
     * A run that could not finish is a failure and takes the error tone. It reached the journal
     * rather than throwing — a folder it cannot read is the commonest one — so nothing above
     * would have told the reader otherwise.
     */
    private fun ImportSummary.toast(): ToastMessage = AppText.copy.settings.let { text ->
        when {
            outcome != ImportOutcome.Success -> ToastMessage(
                text = text.scanCouldNotFinish,
                tone = ToastTone.Error,
            )

            found == 0 -> ToastMessage(text.noNewSlips)

            else -> ToastMessage(text.scanFound(found, filed, queued))
        }
    }
}
