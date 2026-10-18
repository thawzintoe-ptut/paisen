package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.agent.ModelState
import com.ptut.expense.domain.elapsedPhrase
import com.ptut.expense.domain.folders.WatchedFolder
import com.ptut.expense.domain.folders.watched
import com.ptut.expense.domain.importFix
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.retentionPhrase
import com.ptut.expense.domain.settings.ImportChoices
import com.ptut.expense.domain.settings.ImportSettings
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.SettingsCopy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Which row. It is the row's identity as well as the destination it opens. */
enum class SettingsAction { SlipSource, BackgroundImport, KeepFailedSlips, Activity, Categories, MoodByTimeOfDay, Transparency, Language, ExportLedger, ImportLedger, Timezone, Assistant, Licences }

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
    /** Which white card the row sits in (p21): importing, then the ledger's words, then its file and day. */
    val group: Int = 0,
    /** Background import's switch where the platform has a schedule to switch (C-4), or null. */
    val switchedOn: Boolean? = null,
)

data class SettingsUiState(val rows: List<SettingsRowUiModel>)

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
    ledger: LedgerRepository,
    private val settings: ImportSettings,
    private val cadence: SlipImportCadence,
    /** The watched folders, named on the Photo folders row (#124, #168). Empty where none is bound. */
    folders: Flow<List<WatchedFolder>> = flowOf(emptyList()),
    /** The reader's categories and sub-categories, counted on the Categories row (#168). */
    categoryCounts: Flow<Pair<Int, Int>> = flowOf(0 to 0),
    /** #207: the model's state, for its row. */
    assistant: Flow<ModelState> = flowOf(ModelState.NotOffered),
    /** A debug build shows the assistant row before any build can offer the model. */
    private val showAssistant: Boolean = false,
) : ViewModel() {

    fun setMoodByTimeOfDay(on: Boolean) = settings.setMoodByTimeOfDay(on)

    fun setTransparency(on: Boolean) = settings.setTransparency(on)

    val uiState: StateFlow<SettingsUiState> = combine(
        ledger.sinceLastImportMillis,
        settings.choices,
        folders,
        categoryCounts,
        assistant,
    ) { since, choices, watched, counts, model -> SettingsUiState(rows(since, choices, watched, counts, model)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(
                rows(since = null, choices = settings.choices.value, watched = emptyList(), counts = 0 to 0, model = ModelState.NotOffered),
            ),
        )

    private fun rows(
        since: Long?,
        choices: ImportChoices,
        watched: List<WatchedFolder>,
        counts: Pair<Int, Int>,
        model: ModelState,
        text: SettingsCopy = AppText.copy.settings,
    ) = listOfNotNull(
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
        // Activity's way in, now Home has no bell (#144, N2).
        SettingsRowUiModel(
            id = SettingsAction.Activity,
            name = text.activity,
            value = since?.let { text.lastImport(elapsedPhrase(it)) } ?: text.noImportYet,
            icon = "notifications",
        ),
        SettingsRowUiModel(
            id = SettingsAction.Categories,
            name = AppText.copy.categories.title,
            value = text.categoryCounts(counts.first, counts.second),
            icon = "label",
            group = 1,
        ),
        // p21's second group: the morning recap and evening receipt, off with one switch (#121).
        SettingsRowUiModel(
            id = SettingsAction.MoodByTimeOfDay,
            name = text.moodByTimeOfDay,
            value = text.moodValue,
            icon = "wb_twilight",
            group = 1,
            switchedOn = choices.moodByTimeOfDay,
        ),
        // Off draws the pill solid. iOS Reduce Transparency does the same without asking (#123).
        SettingsRowUiModel(
            id = SettingsAction.Transparency,
            name = text.transparency,
            value = text.transparencyValue,
            icon = "tune",
            group = 1,
            switchedOn = choices.transparency,
        ),
        SettingsRowUiModel(
            id = SettingsAction.Language,
            name = AppText.copy.language.title,
            value = AppText.language.value.label,
            icon = "translate",
            group = 2,
        ),
        SettingsRowUiModel(
            id = SettingsAction.Timezone,
            name = text.timezone,
            value = text.timezoneValue,
            icon = "schedule",
            group = 2,
        ),
        // "Backup file", not "CSV or backup file": export is JSON only in v1 (#147, S7).
        SettingsRowUiModel(
            id = SettingsAction.ExportLedger,
            name = text.exportLedger,
            value = text.exportValue,
            icon = "download",
            group = 2,
        ),
        SettingsRowUiModel(
            id = SettingsAction.ImportLedger,
            name = text.importLedger,
            value = text.importValue,
            icon = "upload",
            group = 2,
        ),
        // #207: absent until a build can offer the model, so the screen is unchanged without one.
        SettingsRowUiModel(
            id = SettingsAction.Assistant,
            name = text.assistant,
            value = model.statusLine(),
            icon = "auto_awesome",
            group = 3,
        ).takeIf { showAssistant || model != ModelState.NotOffered },
        SettingsRowUiModel(
            id = SettingsAction.Licences,
            name = text.licences,
            value = text.licencesValue,
            icon = "menu_book",
            group = 3,
        ),
    )

    /**
     * Three states, and the wrong one is worse than no line. "Off" is said only where the reader
     * switched it off; "no run yet" rather than "off" when nothing has happened, because the
     * importer is running and the ledger is simply new — saying off would blame a setting for that.
     *
     * A platform with no schedule to switch cannot be off, so its rhythm is read straight through.
     */
    private fun importLine(text: SettingsCopy, since: Long?, choices: ImportChoices): String {
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
