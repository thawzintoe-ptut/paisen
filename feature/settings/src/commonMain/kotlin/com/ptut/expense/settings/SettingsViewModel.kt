package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.agent.ModelState
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.categories.CategoryCounts
import com.ptut.expense.domain.categories.ObserveCategoryCountsUseCase
import com.ptut.expense.domain.folders.ObserveWatchedFoldersUseCase
import com.ptut.expense.domain.folders.WatchedFolder
import com.ptut.expense.domain.folders.watched
import com.ptut.expense.domain.importFix
import com.ptut.expense.domain.ledger.ObserveLastImportUseCase
import com.ptut.expense.domain.play.Goal
import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.settings.DisableAppLockUseCase
import com.ptut.expense.domain.settings.EnableAppLockUseCase
import com.ptut.expense.domain.settings.ObserveAppChoicesUseCase
import com.ptut.expense.domain.settings.ObserveAssistantModelUseCase
import com.ptut.expense.domain.settings.SetMoodByTimeOfDayUseCase
import com.ptut.expense.domain.settings.SetSlipOwnerUseCase
import com.ptut.expense.domain.settings.SetTransparencyUseCase
import com.ptut.expense.domain.settings.SetWeeklyCheckInUseCase
import com.ptut.expense.domain.settings.SwitchNoteToneUseCase
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.NoteTone
import com.ptut.expense.locale.SettingsCopy
import com.ptut.expense.locale.currencyCopy
import com.ptut.expense.locale.incomeSlipCopy
import com.ptut.expense.locale.noteCopy
import com.ptut.expense.ui.elapsedPhrase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which row. It is the row's identity as well as the destination it opens. */
enum class SettingsAction { GoalJar, Advanced, SlipSource, BackgroundImport, KeepFailedSlips, SlipOwner, Activity, Categories, MoodByTimeOfDay, WeeklyCheckIn, NoteTone, Transparency, AppLock, Language, ExportLedger, ImportLedger, Timezone, HomeCurrency, Assistant, Feedback, Licences, Play }

/** Where Send feedback writes to. The same address PRIVACY.md and SECURITY.md give. */
const val FEEDBACK_ADDRESS = "developer.ptut@gmail.com"

/**
 * [id] is the row's identity as well as its destination. It is a field of its own rather than the
 * row's name, because the stale-import notice points at a row and copy is not an address.
 *
 * There is no "this row opens nothing". `SettingsAction.destination` is exhaustive, so a row that
 * reaches this screen has somewhere to go — and a row that answers a tap with nothing reads as the
 * app having failed rather than as a fixed value.
 */
data class SettingsRowUiModel(
    val id: SettingsAction,
    val name: String,
    val value: String,
    val icon: String,
    /**
     * This row owns the fix a silent importer needs. Derived on every emission rather than carried
     * in from whatever sent the reader here, so a lever that has started working again stops being
     * marked without anyone having to navigate away from the screen.
     */
    val needsAttention: Boolean = false,
    /** Background import's switch where the platform has a schedule to switch (C-4), or null. */
    val switchedOn: Boolean? = null,
)

/**
 * #101: Settings keeps what people change in a normal week, then one Advanced row ([More]).
 * [Slips], [Ledger] and [Help] are the Advanced screen's cards.
 */
enum class SettingsGroup { Saving, You, Safe, More, Slips, Ledger, Help }

/** [title] sits above the card; null for the lone Advanced row. */
data class SettingsGroupUiModel(val group: SettingsGroup, val rows: List<SettingsRowUiModel>, val title: String? = null)

data class SettingsUiState(
    val groups: List<SettingsGroupUiModel>,
    /** The Advanced screen's cards, one tap behind [SettingsAction.Advanced]. */
    val advanced: List<SettingsGroupUiModel> = emptyList(),
    /** The open goal the Goal jar row opens, or null to start one. */
    val goalId: String? = null,
) {
    /** Every row on both screens in screen order, for a reader that does not care which card holds it. */
    val rows: List<SettingsRowUiModel> get() = (groups + advanced).flatMap { it.rows }
}

/**
 * NFR-1, NFR-2, NFR-4, NFR-5. The levers that decide whether the ledger can be trusted.
 *
 * It holds no action of its own. Every row opens a screen that owns its own state, which is why
 * this took the archive and the file store out — an operation started here would report its
 * result over whichever screen the reader had moved to.
 *
 * Every value is read, not stored. The import line comes from the journal's own last successful
 * run rather than a `lastImportAt` scalar — F5's argument, and the same reason the freshness chip
 * on Today is derived: a stored copy drifts, and a drifted trust indicator is worse than none.
 */
class SettingsViewModel(
    observeLastImport: ObserveLastImportUseCase,
    observeChoices: ObserveAppChoicesUseCase,
    private val cadence: SlipImportCadence,
    private val saveMoodByTimeOfDay: SetMoodByTimeOfDayUseCase,
    private val saveTransparency: SetTransparencyUseCase,
    private val saveWeeklyCheckIn: SetWeeklyCheckInUseCase,
    private val saveSlipOwner: SetSlipOwnerUseCase,
    private val enableAppLock: EnableAppLockUseCase,
    private val disableAppLock: DisableAppLockUseCase,
    private val switchNoteToneUseCase: SwitchNoteToneUseCase,
    /** The watched folders, named on the Photo folders row (#124, #168). None where nothing is bound. */
    observeFolders: ObserveWatchedFoldersUseCase = ObserveWatchedFoldersUseCase(),
    /** The reader's categories and sub-categories, counted on the Categories row (#168). */
    observeCategoryCounts: ObserveCategoryCountsUseCase = ObserveCategoryCountsUseCase(),
    /** #207: the model's state, for its row. */
    observeAssistant: ObserveAssistantModelUseCase = ObserveAssistantModelUseCase(),
    /** A debug build shows the assistant row before any build can offer the model. */
    private val showAssistant: Boolean = false,
    /** #25: the phone can ask for a fingerprint, face or PIN. Android 8 cannot, so it draws no row. */
    private val canLock: Boolean = false,
    /** #22: the weekly note's tone row, in debug builds until the note has been tried. */
    private val showNoteTone: Boolean = false,
    /** #41: the home currency row, in builds that offer other currencies. */
    private val showHomeCurrency: Boolean = false,
    /** #83: Settings › Play, while Remote Config's play switch is on. */
    private val showPlay: Boolean = false,
    /** #101: the goals behind the Goal jar row. None where play binds no book. */
    observeGoals: Flow<List<Goal>> = flowOf(emptyList()),
) : ViewModel() {

    fun setMoodByTimeOfDay(on: Boolean) = saveMoodByTimeOfDay(on)

    fun setTransparency(on: Boolean) = saveTransparency(on)

    /**
     * #25: on only after the phone's own check passes, so a failed check cannot lock anyone out.
     * [onTurnedOn] runs once the lock is on, so the open session is not locked straight away.
     */
    fun setAppLock(on: Boolean, authenticate: suspend () -> Boolean, onTurnedOn: () -> Unit = {}) {
        if (!on) {
            disableAppLock()
            return
        }
        viewModelScope.launch {
            if (enableAppLock(authenticate)) onTurnedOn()
        }
    }

    fun setWeeklyCheckIn(on: Boolean) = saveWeeklyCheckIn(on)

    /** #22: Plain and Playful, one tap each way. */
    fun switchNoteTone() = switchNoteToneUseCase()

    /** #26: your name as slips print it; empty starts learning again. */
    fun setSlipOwner(name: String?) = saveSlipOwner(name)

    val uiState: StateFlow<SettingsUiState> = combine(
        observeLastImport(),
        observeChoices(),
        observeFolders(),
        observeCategoryCounts(),
        observeAssistant(),
        ::Inputs,
    ).combine(observeGoals) { inputs, goals -> state(inputs, goals.firstOrNull { it.archivedAtEpochMillis == null }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS),
            initialValue = state(
                Inputs(since = null, choices = observeChoices().value, watched = emptyList(), counts = CategoryCounts(), model = ModelState.NotOffered),
                goal = null,
            ),
        )

    private data class Inputs(val since: Long?, val choices: AppChoices, val watched: List<WatchedFolder>, val counts: CategoryCounts, val model: ModelState)

    private fun state(inputs: Inputs, goal: Goal?, text: SettingsCopy = AppText.copy.settings): SettingsUiState {
        val (since, choices, watched, counts, model) = inputs
        val advanced = listOf(
            SettingsGroupUiModel(SettingsGroup.Slips, slipRows(since, choices, watched, text), text.groupSlips),
            SettingsGroupUiModel(SettingsGroup.Ledger, ledgerRows(choices, counts, model, text), text.groupLedger),
            SettingsGroupUiModel(SettingsGroup.Help, helpRows(text), text.groupHelp),
        ).filter { it.rows.isNotEmpty() }
        val main = listOf(
            SettingsGroupUiModel(SettingsGroup.Saving, savingRows(choices, goal, text), text.groupSaving),
            SettingsGroupUiModel(SettingsGroup.You, youRows(choices, text), text.groupYou),
            SettingsGroupUiModel(SettingsGroup.Safe, safeRows(choices, text), text.groupSafe),
            SettingsGroupUiModel(
                SettingsGroup.More,
                listOf(
                    SettingsRowUiModel(
                        id = SettingsAction.Advanced,
                        name = text.advanced,
                        value = text.advancedValue,
                        icon = "tune",
                        // A stale import's fix sits behind this row, so the row says so too.
                        needsAttention = advanced.any { group -> group.rows.any { it.needsAttention } },
                    ),
                ),
            ),
        ).filter { it.rows.isNotEmpty() }
        return SettingsUiState(groups = main, advanced = advanced, goalId = goal?.id)
    }

    private fun savingRows(choices: AppChoices, goal: Goal?, text: SettingsCopy) = listOfNotNull(
        // #101: the jar is play's, so it shows only while play is on.
        SettingsRowUiModel(
            id = SettingsAction.GoalJar,
            name = AppText.copy.play.extras.jarTitle,
            value = goal?.name ?: text.goalJarNone,
            icon = "cash",
        ).takeIf { showPlay && choices.play.on },
        // #83: a screen of its own (p22), so its master switch sits there and not on this row.
        AppText.copy.play.settings.let { play ->
            SettingsRowUiModel(
                id = SettingsAction.Play,
                name = play.title,
                value = if (choices.play.on) play.masterOn else play.masterOff,
                icon = "local_activity",
            ).takeIf { showPlay }
        },
    )

    private fun youRows(choices: AppChoices, text: SettingsCopy) = listOf(
        SettingsRowUiModel(
            id = SettingsAction.Language,
            name = AppText.copy.language.title,
            value = AppText.language.value.label,
            icon = "translate",
        ),
        // p21's second group: the morning recap and evening receipt, off with one switch (#121).
        SettingsRowUiModel(
            id = SettingsAction.MoodByTimeOfDay,
            name = text.moodByTimeOfDay,
            value = text.moodValue,
            icon = "wb_twilight",
            switchedOn = choices.moodByTimeOfDay,
        ),
    )

    private fun safeRows(choices: AppChoices, text: SettingsCopy) = listOfNotNull(
        // "Backup file", not "CSV or backup file": export is JSON only in v1 (#147, S7).
        SettingsRowUiModel(
            id = SettingsAction.ExportLedger,
            name = text.exportLedger,
            value = text.exportValue,
            icon = "download",
        ),
        // #25: turning it on asks for the fingerprint, face or PIN first, so a failed check cannot lock anyone out.
        SettingsRowUiModel(
            id = SettingsAction.AppLock,
            name = text.appLock,
            value = text.appLockValue,
            icon = "lock",
            switchedOn = choices.appLock,
        ).takeIf { canLock },
    )

    private fun slipRows(since: Long?, choices: AppChoices, watched: List<WatchedFolder>, text: SettingsCopy) = listOf(
        SettingsRowUiModel(
            id = SettingsAction.SlipSource,
            name = text.photoFolders,
            // The folders by name, "Screenshots, K PLUS" (p21), once any is watched.
            value = watched.watched().joinToString(", ") { it.name }.ifEmpty { cadence.slipSource },
            icon = "folder",
            needsAttention = ownsTheFix(since, SettingsAction.SlipSource),
        ),
        SettingsRowUiModel(
            id = SettingsAction.BackgroundImport,
            name = text.backgroundImport,
            value = importLine(text, since, choices),
            icon = "sync",
            needsAttention = ownsTheFix(since, SettingsAction.BackgroundImport),
            // C-4: only a platform with a schedule draws a switch; iOS keeps its explanation.
            switchedOn = choices.backgroundImport.takeIf { cadence.importIsAdjustable },
        ),
        SettingsRowUiModel(
            id = SettingsAction.KeepFailedSlips,
            name = text.keepUnreadSlips,
            value = text.thenDeleted(retentionPhrase(choices.retention)),
            icon = "auto_delete",
        ),
        // #26: whose name marks a slip as money in. Learned from your own slips, correctable here.
        SettingsRowUiModel(
            id = SettingsAction.SlipOwner,
            name = incomeSlipCopy().ownerRow,
            value = choices.slipOwner.name ?: incomeSlipCopy().ownerUnknown,
            icon = "call_received",
        ),
        // Activity's way in, now Home has no bell (#144, N2).
        SettingsRowUiModel(
            id = SettingsAction.Activity,
            name = text.activity,
            value = since?.let { text.lastImport(elapsedPhrase(it)) } ?: text.noImportYet,
            icon = "notifications",
        ),
    )

    private fun ledgerRows(choices: AppChoices, counts: CategoryCounts, model: ModelState, text: SettingsCopy) = listOfNotNull(
        SettingsRowUiModel(
            id = SettingsAction.Categories,
            name = AppText.copy.categories.title,
            value = text.categoryCounts(counts.categories, counts.subCategories),
            icon = "label",
        ),
        SettingsRowUiModel(
            id = SettingsAction.HomeCurrency,
            name = currencyCopy().homeTitle,
            value = Home.choice.value.let { currencyCopy().homeRowValue(it.money.code, it.zoneId) },
            icon = "payments",
        ).takeIf { showHomeCurrency },
        SettingsRowUiModel(
            id = SettingsAction.Timezone,
            name = text.timezone,
            value = text.timezoneValue(Home.choice.value.zoneId),
            icon = "schedule",
        ),
        SettingsRowUiModel(
            id = SettingsAction.ImportLedger,
            name = text.importLedger,
            value = text.importValue,
            icon = "upload",
        ),
        // #20: Monday's look back at last week on Today.
        SettingsRowUiModel(
            id = SettingsAction.WeeklyCheckIn,
            name = text.weeklyCheckIn,
            value = text.weeklyCheckInValue,
            icon = "calendar_month",
            switchedOn = choices.weeklyCheckIn,
        ),
        SettingsRowUiModel(
            id = SettingsAction.NoteTone,
            name = noteCopy().toneTitle,
            value = if (choices.noteTone == NoteTone.Playful) noteCopy().tonePlayful else noteCopy().tonePlain,
            icon = "tune",
        ).takeIf { showNoteTone },
        // Off draws the pill solid. iOS Reduce Transparency does the same without asking (#123).
        SettingsRowUiModel(
            id = SettingsAction.Transparency,
            name = text.transparency,
            value = text.transparencyValue,
            icon = "tune",
            switchedOn = choices.transparency,
        ),
        // #207: absent until a build can offer the model, so the screen is unchanged without one.
        SettingsRowUiModel(
            id = SettingsAction.Assistant,
            name = text.assistant,
            value = model.statusLine(),
            icon = "auto_awesome",
        ).takeIf { showAssistant || model != ModelState.NotOffered },
    )

    private fun helpRows(text: SettingsCopy) = listOf(
        // The address is the value, so a phone with no mail app still shows where to write.
        SettingsRowUiModel(
            id = SettingsAction.Feedback,
            name = text.feedback,
            value = FEEDBACK_ADDRESS,
            icon = "help",
        ),
        SettingsRowUiModel(
            id = SettingsAction.Licences,
            name = text.licences,
            value = text.licencesValue,
            icon = "menu_book",
        ),
    )

    /**
     * Three states, and the wrong one is worse than no line. "Off" is said only where the reader
     * switched it off; "no run yet" rather than "off" when nothing has happened, because the
     * importer is running and the ledger is simply new — saying off would blame a setting for that.
     *
     * A platform with no schedule to switch cannot be off, so its rhythm is read straight through.
     */
    private fun importLine(text: SettingsCopy, since: Long?, choices: AppChoices): String {
        if (cadence.importIsAdjustable && !choices.backgroundImport) {
            return text.importOff
        }
        val lastRun = since?.let { text.lastRunInRow(elapsedPhrase(it)) } ?: text.noRunYet
        return "${cadence.importRhythm} · $lastRun"
    }

    /**
     * Asked of the row's identity. A lever explains a silent importer whatever the platform can do
     * about it, so a row that can only describe its schedule is still the one the notice points at.
     */
    private fun ownsTheFix(since: Long?, row: SettingsAction) = importFix(since)?.owningRow() == row
}
