package com.ptut.expense.settings

import app.cash.turbine.test
import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.ObserveLastImportUseCase
import com.ptut.expense.domain.play.Goal
import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.domain.settings.DisableAppLockUseCase
import com.ptut.expense.domain.settings.EnableAppLockUseCase
import com.ptut.expense.domain.settings.ObserveAppChoicesUseCase
import com.ptut.expense.domain.settings.PlayChoices
import com.ptut.expense.domain.settings.SetMoodByTimeOfDayUseCase
import com.ptut.expense.domain.settings.SetSlipOwnerUseCase
import com.ptut.expense.domain.settings.SetTransparencyUseCase
import com.ptut.expense.domain.settings.SetWeeklyCheckInUseCase
import com.ptut.expense.domain.settings.SwitchNoteToneUseCase
import com.ptut.expense.domain.settings.appSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

private const val DAY = 24L * 60 * 60 * 1000
private const val TWO_DAYS = 2 * DAY
private const val FOUR_DAYS = 4 * DAY

class SettingsViewModelTest : ViewModelTest() {

    @Test
    fun `the rows read in screen order with Settings before Advanced`() = runTest {
        val rows = viewModel().uiState.value.rows

        assertEquals(
            listOf(
                "Language",
                "Backup",
                "Advanced",
                "Help and feedback",
                "Photo folders",
                "Background import",
                "Keep unread slips",
                "Your name on slips",
                "Categories",
                "Timezone",
                "Import log",
                "Restore from a backup",
                "Weekly check-in",
                "Transparency",
                "Mood by time of day",
                "Send feedback",
                "Licences",
            ),
            rows.map { it.name },
        )
    }

    @Test
    fun `settings keeps the everyday cards and puts the rest one tap behind Advanced`() = runTest {
        val state = viewModel(showPlay = true).uiState.value

        assertEquals(
            listOf(
                SettingsGroup.Saving to listOf("Goal jar", "Play"),
                SettingsGroup.You to listOf("Language"),
                SettingsGroup.Safe to listOf("Backup", "Advanced"),
                SettingsGroup.Account to listOf("Help and feedback"),
            ),
            state.groups.map { group -> group.group to group.rows.map { it.name } },
        )
        assertEquals(
            listOf(
                SettingsGroup.Slips to listOf("Photo folders", "Background import", "Keep unread slips", "Your name on slips"),
                SettingsGroup.Ledger to listOf("Categories", "Timezone", "Import log", "Restore from a backup", "Weekly check-in"),
                SettingsGroup.Look to listOf("Transparency", "Mood by time of day"),
            ),
            state.advanced.map { group -> group.group to group.rows.map { it.name } },
        )
        assertEquals(listOf(SettingsAction.Feedback, SettingsAction.Licences), state.help.map { it.id })
    }

    @Test
    fun `the help row says what waits behind it and the advanced row says what it tunes`() = runTest {
        val rows = viewModel().uiState.value.rows

        assertEquals("Send feedback, licences", rows.row(SettingsAction.Help).value)
        assertEquals("Slip reading, folders, rules and more", rows.row(SettingsAction.Advanced).value)
    }

    @Test
    fun `the backup row says the day of the last copy once there is one`() = runTest {
        assertEquals("Backup file", rowsFor().row(SettingsAction.ExportLedger).value)

        val rows = rowsFor(settings = appSettings(AppChoices(lastExportOn = "2026-11-01")))

        assertEquals("Last copy Sun 1 Nov", rows.row(SettingsAction.ExportLedger).value)
    }

    @Test
    fun `the goal jar row stays while play is off`() = runTest {
        val settings = appSettings(AppChoices(play = PlayChoices(on = false)))

        val saving = viewModel(settings = settings, showPlay = true).uiState.value.groups.single { it.group == SettingsGroup.Saving }.rows

        assertEquals(listOf(SettingsAction.GoalJar, SettingsAction.Play), saving.map { it.id })
    }

    @Test
    fun `a build that holds play back keeps the goal jar row alone`() = runTest {
        val saving = viewModel(showPlay = false, showJar = true).uiState.value.groups.single { it.group == SettingsGroup.Saving }.rows

        assertEquals(listOf(SettingsAction.GoalJar), saving.map { it.id })
    }

    @Test
    fun `without v2 Advanced drops import control and categories`() = runTest {
        val hidden = setOf(SettingsAction.SlipSource, SettingsAction.BackgroundImport, SettingsAction.KeepFailedSlips, SettingsAction.Categories, SettingsAction.Activity)

        val shown = rowsFor(sinceLastImport = TWO_DAYS, showV2 = false).map { it.id }

        assertEquals(emptyList(), shown.filter { it in hidden })
        assertTrue(SettingsAction.ImportLedger in shown)
    }

    @Test
    fun `without v2 the row that owns a stopped import's fix still shows`() = runTest {
        assertEquals(listOf("Advanced", "Photo folders"), rowsFor(sinceLastImport = null, showV2 = false).filter { it.needsAttention }.map { it.name })
        assertEquals(listOf("Advanced", "Background import"), rowsFor(sinceLastImport = FOUR_DAYS, showV2 = false).filter { it.needsAttention }.map { it.name })
    }

    @Test
    fun `the goal jar row names the open goal and opens it`() = runTest {
        val goal = Goal(id = "g1", name = "Chiang Mai", target = Satang(500_000L), currency = "THB", start = LocalDate(2026, 11, 1), createdAtEpochMillis = 0L)
        val uiState = viewModel(showPlay = true, goals = flowOf(listOf(goal))).uiState
        backgroundScope.launch { uiState.collect { } }
        runCurrent()

        assertEquals("Chiang Mai", uiState.value.rows.row(SettingsAction.GoalJar).value)
        assertEquals("g1", uiState.value.goalId)
    }

    @Test
    fun `every row on both screens names an icon the app can draw`() = runTest {
        val goal = Goal(id = "g1", name = "Chiang Mai", target = Satang(500_000L), currency = "THB", start = LocalDate(2026, 11, 1), createdAtEpochMillis = 0L)
        val uiState = viewModel(showPlay = true, goals = flowOf(listOf(goal))).uiState
        backgroundScope.launch { uiState.collect { } }
        runCurrent()

        val unknown = uiState.value.rows.filter { com.ptut.expense.design.PtutIcon.find(it.icon) == null }
        assertEquals(emptyList(), unknown.map { "${it.id}: ${it.icon}" })
    }

    @Test
    fun `the play row says off when the master switch is off`() = runTest {
        val settings = appSettings(AppChoices(play = PlayChoices(on = false)))

        val play = viewModel(settings = settings, showPlay = true).uiState.value.rows.single { it.id == SettingsAction.Play }

        assertEquals("Off: PaiSeN shows money only", play.value)
    }

    /** The address is the row's value, so a phone with no mail app still shows where to write. */
    @Test
    fun `the feedback row shows the address it writes to`() = runTest {
        val row = viewModel().uiState.value.rows.first { it.id == SettingsAction.Feedback }

        assertEquals(FEEDBACK_ADDRESS, row.value)
    }

    @Test
    fun `a ledger with no import yet says so rather than reading as switched off`() = runTest {
        assertEquals("On · no run yet", viewModel(sinceLastImport = null).uiState.value.rows.row(SettingsAction.BackgroundImport).value)
    }

    @Test
    fun `a schedule the reader switched off says off and says what still runs`() = runTest {
        val rows = rowsFor(
            sinceLastImport = FOUR_DAYS,
            settings = appSettings(AppChoices(backgroundImport = false)),
        )

        assertEquals("Off · scans when you open the app", rows.row(SettingsAction.BackgroundImport).value)
    }

    /** C-4: a platform with no schedule cannot be off, so the choice must not reach its line. */
    @Test
    fun `a platform with no schedule reads its own rhythm whatever the choice says`() = runTest {
        val rows = rowsFor(
            sinceLastImport = null,
            cadence = foregroundOnly(),
            settings = appSettings(AppChoices(backgroundImport = false)),
        )

        assertEquals("On each app open · no run yet", rows.row(SettingsAction.BackgroundImport).value)
    }

    @Test
    fun `the retention row says the number the reader chose`() = runTest {
        val rows = rowsFor(settings = appSettings(AppChoices(retention = 30.days)))

        assertEquals("30 days, then removed from PaiSeN", rows.row(SettingsAction.KeepFailedSlips).value)
    }

    @Test
    fun `a ledger with no import yet marks the source rather than the schedule`() = runTest {
        assertEquals(listOf("Advanced", "Photo folders"), marked(sinceLastImport = null))
    }

    @Test
    fun `an importer that ran and then stopped marks the schedule instead`() = runTest {
        assertEquals(listOf("Advanced", "Background import"), marked(sinceLastImport = FOUR_DAYS))
    }

    @Test
    fun `a quiet stretch under three days marks no row at all`() = runTest {
        assertEquals(emptyList(), marked(sinceLastImport = TWO_DAYS))
    }

    @Test
    fun `the last run is reported whatever the platform's rhythm is called`() = runTest {
        viewModel(sinceLastImport = 120_000L, cadence = foregroundOnly()).uiState.test {
            assertEquals("On each app open · no run yet", awaitItem().rows.row(SettingsAction.BackgroundImport).value)
            assertEquals("On each app open · last run 2 min ago", awaitItem().rows.row(SettingsAction.BackgroundImport).value)
        }
    }

    @Test
    fun `a platform that only catches up on open still names where slips come from`() = runTest {
        val rows = viewModel(cadence = foregroundOnly()).uiState.value.rows

        assertEquals("K PLUS album · no background scan on iOS", rows.row(SettingsAction.SlipSource).value)
    }

    /**
     * Both actuals, held to what every screen assumes of them: a row renders two lines of copy and
     * a blank one would draw an empty second line rather than fail anywhere a test could see it.
     */
    @Test
    fun `this platform's cadence says something in every line`() {
        val cadence = slipImportCadence()

        // Shown only when nothing is watched, so it names no folder (#124).
        assertTrue(cadence.slipSource.startsWith("No "), cadence.slipSource)
        assertTrue(cadence.importRhythm.isNotBlank(), "the import row would render a blank line")
        assertTrue(cadence.importExplanation.isNotBlank(), "the screen would open on nothing")
    }

    @Test
    fun `a phone that can ask for a fingerprint or PIN shows the app lock switch as stored`() = runTest {
        val settings = appSettings(AppChoices(appLock = true))
        val uiState = viewModel(settings = settings, canLock = true).uiState
        backgroundScope.launch { uiState.collect { } }
        runCurrent()

        assertEquals(true, uiState.value.rows.first { it.id == SettingsAction.AppLock }.switchedOn)
    }

    @Test
    fun `a phone that cannot ask draws no app lock row`() = runTest {
        assertTrue(rowsFor().none { it.id == SettingsAction.AppLock })
    }

    @Test
    fun `the app lock turns on only after the phone check passes and then unlocks this session`() = runTest {
        val settings = appSettings()
        var unlocked = false

        viewModel(settings = settings).setAppLock(on = true, authenticate = { true }, onTurnedOn = { unlocked = true })
        runCurrent()

        assertEquals(true, settings.choices.value.appLock)
        assertTrue(unlocked)
    }

    @Test
    fun `a failed check leaves the app lock off`() = runTest {
        val settings = appSettings()
        var unlocked = false

        viewModel(settings = settings).setAppLock(on = true, authenticate = { false }, onTurnedOn = { unlocked = true })
        runCurrent()

        assertEquals(false, settings.choices.value.appLock)
        assertEquals(false, unlocked)
    }

    @Test
    fun `turning the app lock off asks for no check`() = runTest {
        val settings = appSettings(AppChoices(appLock = true))
        var asked = false

        viewModel(settings = settings).setAppLock(on = false, authenticate = {
            asked = true
            true
        })
        runCurrent()

        assertEquals(false, settings.choices.value.appLock)
        assertEquals(false, asked)
    }

    private fun TestScope.marked(sinceLastImport: Long?): List<String> =
        rowsFor(sinceLastImport).filter { it.needsAttention }.map { it.name }

    /**
     * The rows are shared `WhileSubscribed`, so `uiState.value` is the initial value until
     * something collects. Reading it straight would answer for a null journal every time.
     */
    private fun TestScope.rowsFor(
        sinceLastImport: Long? = null,
        cadence: SlipImportCadence = scheduled(),
        settings: AppSettings = appSettings(),
        showV2: Boolean = true,
    ): List<SettingsRowUiModel> {
        val uiState = viewModel(sinceLastImport, cadence, settings, showV2 = showV2).uiState
        backgroundScope.launch { uiState.collect { } }
        runCurrent()
        return uiState.value.rows
    }

    private fun scheduled() = SlipImportCadence(
        slipSource = "K PLUS folder · scanning every 15 min",
        importRhythm = "On",
        importIsAdjustable = true,
        importExplanation = "about every 15 minutes",
    )

    private fun foregroundOnly() = SlipImportCadence(
        slipSource = "K PLUS album · no background scan on iOS",
        importRhythm = "On each app open",
        importIsAdjustable = false,
        importExplanation = "no window while the app is closed",
    )

    private fun viewModel(
        sinceLastImport: Long? = null,
        cadence: SlipImportCadence = scheduled(),
        settings: AppSettings = appSettings(),
        canLock: Boolean = false,
        showPlay: Boolean = false,
        showJar: Boolean = showPlay,
        showV2: Boolean = true,
        goals: Flow<List<Goal>> = flowOf(emptyList()),
    ) = SettingsViewModel(
        observeLastImport = ObserveLastImportUseCase(FakeLedger(sinceLastImport = sinceLastImport)),
        observeChoices = ObserveAppChoicesUseCase(settings),
        cadence = cadence,
        saveMoodByTimeOfDay = SetMoodByTimeOfDayUseCase(settings),
        saveTransparency = SetTransparencyUseCase(settings),
        saveWeeklyCheckIn = SetWeeklyCheckInUseCase(settings),
        saveSlipOwner = SetSlipOwnerUseCase(settings),
        enableAppLock = EnableAppLockUseCase(settings),
        disableAppLock = DisableAppLockUseCase(settings),
        switchNoteToneUseCase = SwitchNoteToneUseCase(settings),
        canLock = canLock,
        showPlay = showPlay,
        showJar = showJar,
        showV2 = showV2,
        observeGoals = goals,
    )

    private fun List<SettingsRowUiModel>.row(id: SettingsAction) = single { it.id == id }
}
