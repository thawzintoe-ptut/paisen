package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.ledger.ObserveLastImportUseCase
import com.ptut.expense.domain.settings.ImportNowResult
import com.ptut.expense.domain.settings.ObserveAppChoicesUseCase
import com.ptut.expense.domain.settings.RunImportNowUseCase
import com.ptut.expense.domain.settings.SetBackgroundImportUseCase
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import com.ptut.expense.ui.elapsedPhrase
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
 * The switch writes the choice before it touches the platform's schedule; see [SetBackgroundImportUseCase].
 */
class BackgroundImportViewModel(
    observeLastImport: ObserveLastImportUseCase,
    observeChoices: ObserveAppChoicesUseCase,
    private val setBackgroundImport: SetBackgroundImportUseCase,
    private val runImportNow: RunImportNowUseCase,
    private val toasts: ToastMessages,
    private val cadence: SlipImportCadence,
) : ViewModel() {

    private val checking = MutableStateFlow(false)

    val uiState: StateFlow<BackgroundImportUiState> = combine(
        observeLastImport(),
        observeChoices(),
        checking,
    ) { since, choices, scanning ->
        state(since, choices.backgroundImport, scanning)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS),
        initialValue = state(
            since = null,
            running = observeChoices().value.backgroundImport,
            checking = false,
        ),
    )

    /**
     * Switching off cancels the job and leaves the app scanning on open, which is what the copy
     * on the screen promises. Switching on schedules and scans at once — a switch that changes
     * nothing visible for fifteen minutes reads as a switch that did not work.
     */
    fun setRunning(running: Boolean) = setBackgroundImport(running)

    /** The reader is here because nothing arrived, so the screen offers the run rather than a wait. */
    fun checkNow() {
        if (checking.value) return
        checking.value = true
        viewModelScope.launch {
            try {
                toasts.show(runImportNow().toast())
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
     * A run that found nothing is not a failure, and saying "0 slips" makes it read as one. A run
     * that could not finish reached the journal rather than throwing, so nothing else would tell
     * the reader. One that could not run at all is the ledger failing; the copy never carries the
     * exception's English.
     */
    private fun ImportNowResult.toast(): ToastMessage = AppText.copy.settings.let { text ->
        when (this) {
            ImportNowResult.CouldNotRun -> ToastMessage(text = text.scanCouldNotRun, tone = ToastTone.Error)
            ImportNowResult.CouldNotFinish -> ToastMessage(text = text.scanCouldNotFinish, tone = ToastTone.Error)
            ImportNowResult.NothingNew -> ToastMessage(text.noNewSlips)
            is ImportNowResult.Found -> ToastMessage(text.scanFound(found, filed, queued))
        }
    }
}
