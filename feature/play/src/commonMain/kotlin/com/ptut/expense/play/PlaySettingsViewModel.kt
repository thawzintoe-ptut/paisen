package com.ptut.expense.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.play.ObservePlayChoicesUseCase
import com.ptut.expense.domain.play.ObservePlayOnUseCase
import com.ptut.expense.domain.play.PlanPlayReminderUseCase
import com.ptut.expense.domain.play.PlayPart
import com.ptut.expense.domain.play.SetPlayChoicesUseCase
import com.ptut.expense.domain.play.SetStampsOffUseCase
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.settings.PlayChoices
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.PlaySettingsCopy
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** p22's switches. The row's identity, as `SettingsAction` is for Settings. */
enum class PlaySetting { Master, Run, Guess, Challenges, GroupStamps, ShareAmounts, WeeklyReminder }

data class PlaySettingRowUiModel(val id: PlaySetting, val name: String, val line: String, val on: Boolean)

data class PlaySettingsGroupUiModel(val title: String, val rows: List<PlaySettingRowUiModel>)

/** [groups] and [footer] are empty while the master is off: p22 then shows the master alone. */
data class PlaySettingsUiState(
    val title: String,
    val master: PlaySettingRowUiModel,
    val groups: List<PlaySettingsGroupUiModel>,
    val footer: String?,
)

/**
 * #83, Settings › Play (p22). A row shows only when this build offers its part (D17), and every
 * switch writes the stored set at once, so there is nothing to save.
 */
class PlaySettingsViewModel(
    private val observeChoices: ObservePlayChoicesUseCase,
    private val setChoices: SetPlayChoicesUseCase,
    /** Whether this build offers a part (D17), before the reader's switches. */
    private val playOn: ObservePlayOnUseCase,
    /** D5: the group opt-out follows the switch to every group. Null in a graph with no shared groups. */
    private val setStampsOff: SetStampsOffUseCase? = null,
    /** D15: the weekly reminder is planned again whenever a switch it depends on moves. */
    private val planReminder: PlanPlayReminderUseCase? = null,
) : ViewModel() {

    val uiState: StateFlow<PlaySettingsUiState> = observeChoices()
        .map(::state)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), state(observeChoices.now()))

    fun set(setting: PlaySetting, on: Boolean) {
        setChoices { choices ->
            when (setting) {
                PlaySetting.Master -> choices.copy(on = on)
                PlaySetting.Run -> choices.copy(run = on)
                PlaySetting.Guess -> choices.copy(guess = on)
                PlaySetting.Challenges -> choices.copy(challenges = on)
                PlaySetting.GroupStamps -> choices.copy(groupStamps = on)
                PlaySetting.ShareAmounts -> choices.copy(shareAmounts = on)
                PlaySetting.WeeklyReminder -> choices.copy(weeklyReminder = on)
            }
        }
        // The master switch sits above both: off, the groups hear the opt-out and the reminder is dropped.
        if (setting in STAMP_SETTINGS) setStampsOff?.let { apply -> viewModelScope.launch { runSuspendCatching { apply() } } }
        if (setting in REMINDER_SETTINGS) planReminder?.let { plan -> viewModelScope.launch { runSuspendCatching { plan() } } }
    }

    private fun offered(part: PlayPart): Boolean = playOn.offered(part)

    private fun state(choices: PlayChoices, text: PlaySettingsCopy = AppText.copy.play.settings): PlaySettingsUiState {
        val master = PlaySettingRowUiModel(
            id = PlaySetting.Master,
            name = text.title,
            line = if (choices.on) text.masterOn else text.masterOff,
            on = choices.on,
        )
        if (!choices.on) return PlaySettingsUiState(text.title, master, groups = emptyList(), footer = null)
        val groups = listOf(
            PlaySettingsGroupUiModel(
                text.onToday,
                listOfNotNull(
                    PlaySettingRowUiModel(PlaySetting.Run, text.run, text.runLine, choices.run).takeIf { offered(PlayPart.Run) },
                    PlaySettingRowUiModel(PlaySetting.Guess, text.guess, text.guessLine, choices.guess).takeIf { offered(PlayPart.Guess) },
                    PlaySettingRowUiModel(PlaySetting.Challenges, text.challenges, text.challengesLine, choices.challenges)
                        .takeIf { offered(PlayPart.Challenge) },
                ),
            ),
            PlaySettingsGroupUiModel(
                text.inGroups,
                listOfNotNull(
                    PlaySettingRowUiModel(PlaySetting.GroupStamps, text.groupStamps, text.groupStampsLine, choices.groupStamps)
                        .takeIf { offered(PlayPart.AllSquare) || offered(PlayPart.Awards) },
                ),
            ),
            PlaySettingsGroupUiModel(
                text.sharing,
                listOfNotNull(
                    PlaySettingRowUiModel(PlaySetting.ShareAmounts, text.shareAmounts, text.shareAmountsLine, choices.shareAmounts)
                        .takeIf { offered(PlayPart.Story) || offered(PlayPart.TripRecap) },
                    PlaySettingRowUiModel(PlaySetting.WeeklyReminder, text.reminder, text.reminderLine, choices.weeklyReminder)
                        .takeIf { offered(PlayPart.Reminder) },
                ),
            ),
        ).filter { it.rows.isNotEmpty() }
        return PlaySettingsUiState(text.title, master, groups, footer = text.footer)
    }
}

private val STAMP_SETTINGS = setOf(PlaySetting.Master, PlaySetting.GroupStamps)

/** The reminder only fires for a week with a guess to make, so Guess going off drops it too. */
private val REMINDER_SETTINGS = setOf(PlaySetting.Master, PlaySetting.Guess, PlaySetting.WeeklyReminder)
