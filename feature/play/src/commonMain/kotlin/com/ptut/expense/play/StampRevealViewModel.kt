package com.ptut.expense.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.play.ObserveStampUseCase
import com.ptut.expense.domain.play.RevealStampUseCase
import com.ptut.expense.domain.play.StampReveal
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RevealUiState {
    data object Loading : RevealUiState

    data class Missing(val line: String) : RevealUiState

    /** The card is drawn face down until [turned]; the stamp is known either way, so the back is ready. */
    data class Loaded(
        val title: String,
        val hint: String,
        val turned: Boolean,
        val cardLabel: String,
        val stamp: StampUiModel,
        val name: String,
        val set: SetProgressUiModel,
        val done: String,
    ) : RevealUiState
}

/** #88, p08: a face-down card that turns over to press today's stamp into the book. */
class StampRevealViewModel(
    private val stampId: String,
    observeStamp: ObserveStampUseCase,
    private val revealStamp: RevealStampUseCase,
    private val toasts: ToastMessages? = null,
) : ViewModel() {

    val uiState: StateFlow<RevealUiState> = observeStamp(stampId)
        .map { reveal -> reveal?.loaded() ?: RevealUiState.Missing(AppText.copy.play.stamps.missing) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), RevealUiState.Loading)

    /** The book keeps only the first turn, so a second tap changes nothing. */
    fun turn() {
        viewModelScope.launch { runSuspendCatching { revealStamp(stampId) }.onFailure { toasts?.show(ToastMessage(AppText.copy.common.notDone, tone = ToastTone.Error)) } }
    }

    private fun StampReveal.loaded(): RevealUiState.Loaded {
        val text = AppText.copy.play.stamps
        val turned = stamp.revealedAtEpochMillis != null
        return RevealUiState.Loaded(
            title = text.revealTitle,
            hint = if (turned) text.turnedHint else text.faceDownHint,
            turned = turned,
            cardLabel = text.cardLabel,
            stamp = stamp.asUiModel(text),
            name = text.names[stamp.kind.name].orEmpty(),
            set = set.asUiModel(text),
            done = text.putInBook,
        )
    }
}
