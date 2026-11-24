package com.ptut.expense.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.format
import com.ptut.expense.domain.play.ChallengeChoices
import com.ptut.expense.domain.play.ChallengeLevel
import com.ptut.expense.domain.play.GetChallengeChoicesUseCase
import com.ptut.expense.domain.play.PickChallengeUseCase
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChallengeOptionUiModel(val categoryId: String, val level: ChallengeLevel, val levelLabel: String, val line: String, val limit: String, val picked: Boolean)

data class ChallengeGroupUiModel(val name: String, val icon: String, val options: List<ChallengeOptionUiModel>)

/** The week's challenge as it stands: the line, money so far and days left, and how full its meter is. */
data class CurrentChallengeUiModel(val line: String, val progress: String, val fraction: Float, val over: String?)

data class ChallengeUiState(
    val title: String,
    val pick: String,
    val groups: List<ChallengeGroupUiModel>,
    val current: CurrentChallengeUiModel?,
    val none: String?,
    val note: String,
    val ifMade: String = AppText.copy.play.extras.challengeIfMade,
    val stampFace: String = AppText.copy.play.extras.challengeStampFace,
    val stampCaption: String = AppText.copy.play.extras.challengeStampCaption,
)

/** #91 (p05): one challenge a week, at a level from the reader's own last four weeks. */
class ChallengeViewModel(
    getChoices: GetChallengeChoicesUseCase,
    private val pickChallenge: PickChallengeUseCase,
    categories: Flow<List<Category>>,
    private val toasts: ToastMessages? = null,
) : ViewModel() {

    private var picking = false

    val uiState: StateFlow<ChallengeUiState?> = combine(getChoices(), categories) { choices, named -> choices.asUiState(named) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), null)

    fun pick(categoryId: String, level: ChallengeLevel) {
        if (picking) return
        picking = true
        viewModelScope.launch {
            runSuspendCatching { pickChallenge(categoryId, level) }.onFailure { toasts?.show(ToastMessage(AppText.copy.common.notDone, tone = ToastTone.Error)) }
            picking = false
        }
    }
}

private fun ChallengeChoices.asUiState(categories: List<Category>): ChallengeUiState {
    val text = AppText.copy.play.extras
    val current = picked?.let { challenge ->
        val name = categories.named(challenge.categoryId).first
        val progress = progress
        CurrentChallengeUiModel(
            line = text.challengeRow(name, challenge.limit.format()),
            progress = progress?.let { text.challengeProgress(it.used.format(), it.daysLeft) }.orEmpty(),
            fraction = progress?.used?.ratioTo(challenge.limit) ?: 0f,
            over = text.challengeOver.takeIf { progress?.under == false },
        )
    }
    return ChallengeUiState(
        title = text.challengeTitle,
        pick = text.challengePick,
        groups = options.map { (categoryId, levels) ->
            val (name, icon) = categories.named(categoryId)
            ChallengeGroupUiModel(
                name = name,
                icon = icon,
                options = levels.entries.sortedBy { it.key.ordinal }.map { (level, limit) ->
                    ChallengeOptionUiModel(
                        categoryId = categoryId,
                        level = level,
                        levelLabel = text.challengeLevels[level.name].orEmpty(),
                        line = text.challengeOption(name, limit.format()),
                        limit = limit.format(),
                        picked = picked?.let { it.categoryId == categoryId && it.level == level } == true,
                    )
                },
            )
        },
        current = current,
        none = text.challengeNone.takeIf { options.isEmpty() },
        note = text.challengeNote,
    )
}
