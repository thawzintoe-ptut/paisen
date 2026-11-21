package com.ptut.expense.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.play.GetMoneyScoreUseCase
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One part: its name, points of what it can give, the one action that adds the rest, and its meter. */
data class ScorePartUiModel(val name: String, val points: String, val action: String, val gain: String, val fraction: Float)

data class MoneyScoreUiState(val title: String, val score: String, val outOf: String, val word: String, val fraction: Float, val parts: List<ScorePartUiModel>, val none: String?, val note: String)

/** #99 (p21): one score from parts the reader can move; it rolls when it changes. Never advice (FR-99.4). */
class MoneyScoreViewModel(getScore: GetMoneyScoreUseCase) : ViewModel() {

    val uiState: StateFlow<MoneyScoreUiState?> = getScore().map { score ->
        val text = AppText.copy.play.extras
        MoneyScoreUiState(
            title = text.scoreTitle,
            score = score.total.toString(),
            outOf = text.scoreOutOf,
            word = text.scoreWord(score.total),
            fraction = score.total.ratio(SCORE_MAX),
            parts = score.parts.map { part ->
                ScorePartUiModel(
                    name = text.scoreParts[part.kind.name].orEmpty(),
                    points = text.scorePart(part.points, part.of),
                    action = text.scoreActions[part.kind.name].orEmpty(),
                    gain = text.scoreGain(part.gain),
                    fraction = part.points.ratio(part.of),
                )
            },
            none = text.scoreNone.takeIf { score.parts.isEmpty() },
            note = text.scoreNote,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), null)
}

private const val SCORE_MAX = 100
