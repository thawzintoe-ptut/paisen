package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.agent.ModelState
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.folders.WatchedFolder
import com.ptut.expense.domain.folders.watched
import com.ptut.expense.domain.importFix
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.domain.shared.Currencies
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
enum class SettingsAction { SlipSource, BackgroundImport, KeepFailedSlips, SlipOwner, Activity, Categories, MoodByTimeOfDay, WeeklyCheckIn, NoteTone, Transparency, AppLock, Language, ExportLedger, ImportLedger, Timezone, HomeCurrency, AddCurrency, Assistant, Feedback, Licences }

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

/** The white cards of p21, top to bottom: importing, the ledger's words, its file and day, then help. */
enum class SettingsGroup { Slips, Daily, Region, Help }

data class SettingsGroupUiModel(val group: SettingsGroup, val rows: List<SettingsRowUiModel>)

data class SettingsUiState(val groups: List<SettingsGroupUiModel>) {
    /** Every row in screen order, for a reader that does not care which card holds it. */
    val rows: List<SettingsRowUiModel> get() = groups.flatMap { it.rows }
}

/** What the Categories row counts (#168). */
data class CategoryCounts(val categories: Int = 0, val subCategories: Int = 0)

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
    private val settings: AppSettings,
    private val cadence: SlipImportCadence,
    /** The watched folders, named on the Photo folders row (#124, #168). Empty where none is bound. */
    folders: Flow<List<WatchedFolder>> = flowOf(emptyList()),
    /** The reader's categories and sub-categories, counted on the Categories row (#168). */
    categoryCounts: Flow<CategoryCounts> = flowOf(CategoryCounts()),
    /** #207: the model's state, for its row. */
    assistant: Flow<ModelState> = flowOf(ModelState.NotOffered),
    /** A debug build shows the assistant row before any build can offer the model. */
    private val showAssistant: Boolean = false,
    /** #25: the phone can ask for a fingerprint, face or PIN. Android 8 cannot, so it draws no row. */
    private val canLock: Boolean = false,
    /** #22: the weekly note's tone row, in debug builds until the note has been tried. */
    private val showNoteTone: Boolean = false,
    /** The Add sheet's starting currency, in builds that offer other currencies there. */
    private val showAddCurrency: Boolean = false,
    /** What the phone's country maps to, for the row's value when nothing is pinned. */
    private val phoneCurrency: () -> String = { Currencies.Thb.code },
) : ViewModel() {

    fun setMoodByTimeOfDay(on: Boolean) = settings.setMoodByTimeOfDay(on)

    fun setTransparency(on: Boolean) = settings.setTransparency(on)

    /**
     * #25: on only after the phone's own check passes, so a failed check cannot lock anyone out.
     * [onTurnedOn] runs once the lock is on, so the open session is not locked straight away.
     */
    fun setAppLock(on: Boolean, authenticate: suspend () -> Boolean, onTurnedOn: () -> Unit = {}) {
        if (!on) {
            settings.setAppLock(false)
            return
        }
        viewModelScope.launch {
            if (authenticate()) {
                settings.setAppLock(true)
                onTurnedOn()
            }
        }
    }

    fun setWeeklyCheckIn(on: Boolean) = settings.setWeeklyCheckIn(on)

    /** #22: Plain and Playful, one tap each way. */
    fun switchNoteTone() = settings.setNoteTone(if (settings.choices.value.noteTone == NoteTone.Plain) NoteTone.Playful else NoteTone.Plain)

    /** #26: your name as slips print it; empty starts learning again. */
    fun setSlipOwner(name: String?) = settings.setSlipOwner(name)

    val uiState: StateFlow<SettingsUiState> = combine(
        ledger.sinceLastImportMillis,
        settings.choices,
        folders,
        categoryCounts,
        assistant,
    ) { since, choices, watched, counts, model -> SettingsUiState(groups(since, choices, watched, counts, model)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS),
            initialValue = SettingsUiState(
                groups(since = null, choices = settings.choices.value, watched = emptyList(), counts = CategoryCounts(), model = ModelState.NotOffered),
            ),
        )

    private fun groups(
        since: Long?,
        choices: AppChoices,
        watched: List<WatchedFolder>,
        counts: CategoryCounts,
        model: ModelState,
        text: SettingsCopy = AppText.copy.settings,
    ) = listOf(
        SettingsGroupUiModel(SettingsGroup.Slips, slipRows(since, choices, watched, text)),
        SettingsGroupUiModel(SettingsGroup.Daily, dailyRows(choices, counts, text)),
        SettingsGroupUiModel(SettingsGroup.Region, regionRows(choices, text)),
        SettingsGroupUiModel(SettingsGroup.Help, helpRows(model, text)),
    ).filter { it.rows.isNotEmpty() }

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

    private fun dailyRows(choices: AppChoices, counts: CategoryCounts, text: SettingsCopy) = listOfNotNull(
        SettingsRowUiModel(
            id = SettingsAction.Categories,
            name = AppText.copy.categories.title,
            value = text.categoryCounts(counts.categories, counts.subCategories),
            icon = "label",
        ),
        // p21's second group: the morning recap and evening receipt, off with one switch (#121).
        SettingsRowUiModel(
            id = SettingsAction.MoodByTimeOfDay,
            name = text.moodByTimeOfDay,
            value = text.moodValue,
            icon = "wb_twilight",
            switchedOn = choices.moodByTimeOfDay,
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
        // #25: turning it on asks for the fingerprint, face or PIN first, so a failed check cannot lock anyone out.
        SettingsRowUiModel(
            id = SettingsAction.AppLock,
            name = text.appLock,
            value = text.appLockValue,
            icon = "lock",
            switchedOn = choices.appLock,
        ).takeIf { canLock },
    )

    private fun regionRows(choices: AppChoices, text: SettingsCopy) = listOfNotNull(
        SettingsRowUiModel(
            id = SettingsAction.Language,
            name = AppText.copy.language.title,
            value = AppText.language.value.label,
            icon = "translate",
        ),
        SettingsRowUiModel(
            id = SettingsAction.Timezone,
            name = text.timezone,
            value = text.timezoneValue(Home.choice.value.zoneId),
            icon = "schedule",
        ),
        SettingsRowUiModel(
            id = SettingsAction.HomeCurrency,
            name = currencyCopy().homeTitle,
            value = Home.choice.value.let { currencyCopy().homeRowValue(it.money.code, it.zoneId) },
            icon = "payments",
        ).takeIf { showAddCurrency },
        SettingsRowUiModel(
            id = SettingsAction.AddCurrency,
            name = currencyCopy().startsIn,
            value = choices.addCurrency ?: currencyCopy().fromPhone(phoneCurrency()),
            icon = "payments",
        ).takeIf { showAddCurrency },
        // "Backup file", not "CSV or backup file": export is JSON only in v1 (#147, S7).
        SettingsRowUiModel(
            id = SettingsAction.ExportLedger,
            name = text.exportLedger,
            value = text.exportValue,
            icon = "download",
        ),
        SettingsRowUiModel(
            id = SettingsAction.ImportLedger,
            name = text.importLedger,
            value = text.importValue,
            icon = "upload",
        ),
    )

    private fun helpRows(model: ModelState, text: SettingsCopy) = listOfNotNull(
        // #207: absent until a build can offer the model, so the screen is unchanged without one.
        SettingsRowUiModel(
            id = SettingsAction.Assistant,
            name = text.assistant,
            value = model.statusLine(),
            icon = "auto_awesome",
        ).takeIf { showAssistant || model != ModelState.NotOffered },
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
