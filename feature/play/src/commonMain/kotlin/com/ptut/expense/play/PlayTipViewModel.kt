package com.ptut.expense.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.play.ObservePlayOnUseCase
import com.ptut.expense.domain.play.PlayTip
import com.ptut.expense.domain.play.SetPlayChoicesUseCase
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.play.TipUiModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One just-in-time tip (V3 section 4). [uiState] is null once it has been seen, while tips are off,
 * or while its feature is not shown. Got it hides this tip for good; Skip all tips turns every tip off.
 */
class PlayTipViewModel(
    private val tip: PlayTip,
    playOn: ObservePlayOnUseCase,
    private val setChoices: SetPlayChoicesUseCase,
) : ViewModel() {

    val uiState: StateFlow<TipUiModel?> = playOn.tip(tip)
        .map { shows -> if (shows) model() else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), null)

    fun gotIt() = setChoices { it.copy(seenTips = it.seenTips + tip.name) }

    fun skipAll() = setChoices { it.copy(tips = false) }

    private fun model(): TipUiModel {
        val text = AppText.copy.play.tips
        val title = text.titles[tip.name].orEmpty()
        return TipUiModel(
            eyebrow = text.eyebrow(tip.number, PlayTip.count),
            title = title,
            body = text.bodies[tip.name].orEmpty(),
            skip = text.skip,
            gotIt = text.gotIt,
            label = text.label(title),
        )
    }
}
