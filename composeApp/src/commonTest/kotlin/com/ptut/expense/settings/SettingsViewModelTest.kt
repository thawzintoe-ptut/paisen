package com.ptut.expense.settings

import app.cash.turbine.test
import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.settings.ImportChoices
import com.ptut.expense.domain.settings.ImportSettings
import com.ptut.expense.domain.settings.importSettings
import com.ptut.expense.nav.PtutRoute
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

private const val DAY = 24L * 60 * 60 * 1000
private const val TWO_DAYS = 2 * DAY
private const val FOUR_DAYS = 4 * DAY

class SettingsViewModelTest : ViewModelTest() {

    @Test
    fun `the rows read in p21's order`() = runTest {
        val rows = viewModel().uiState.value.rows

        assertEquals(
            listOf(
                "Photo folders",
                "Background import",
                "Keep unread slips",
                "Activity",
                "Categories",
                "Mood by time of day",
                "Transparency",
                "Language",
                "Timezone",
                "Export",
                "Import",
                "Licences",
            ),
            rows.map { it.name },
        )
    }

    /**
     * The screen used to carry a slip-language row stating a fact nobody could act on from here.
     * First run is where that instruction belongs, so the row went — and nothing may bring it back
     * without a destination, because the enum has to name one.
     */
    @Test
    fun `every row names a destination except the two switches`() {
        assertEquals(
            listOf(
                PtutRoute.PhotoFolders,
                PtutRoute.BackgroundImport,
                PtutRoute.KeepFailedSlips,
                PtutRoute.Activity,
                PtutRoute.Categories,
                // #121 and #123: switches with no screen behind them; the row's tap flips it.
                null,
                null,
                PtutRoute.Language,
                PtutRoute.ExportData,
                PtutRoute.ImportData,
                PtutRoute.Timezone,
                PtutRoute.AssistantModel,
                PtutRoute.Licences,
            ),
            SettingsAction.entries.map { it.destination() },
        )
    }

    @Test
    fun `a ledger with no import yet says so rather than reading as switched off`() = runTest {
        assertEquals("On · no run yet", viewModel(sinceLastImport = null).uiState.value.rows[1].value)
    }

    @Test
    fun `a schedule the reader switched off says off and says what still runs`() = runTest {
        val rows = rowsFor(
            sinceLastImport = FOUR_DAYS,
            settings = importSettings(ImportChoices(backgroundImport = false)),
        )

        assertEquals("Off · scans when you open the app", rows[1].value)
    }

    /** C-4: a platform with no schedule cannot be off, so the choice must not reach its line. */
    @Test
    fun `a platform with no schedule reads its own rhythm whatever the choice says`() = runTest {
        val rows = rowsFor(
            sinceLastImport = null,
            cadence = foregroundOnly(),
            settings = importSettings(ImportChoices(backgroundImport = false)),
        )

        assertEquals("On each app open · no run yet", rows[1].value)
    }

    @Test
    fun `the retention row says the number the reader chose`() = runTest {
        val rows = rowsFor(settings = importSettings(ImportChoices(retention = 30.days)))

        assertEquals("30 days, then removed from PaiSeN", rows[2].value)
    }

    @Test
    fun `a ledger with no import yet marks the source rather than the schedule`() = runTest {
        assertEquals(listOf("Photo folders"), marked(sinceLastImport = null))
    }

    @Test
    fun `an importer that ran and then stopped marks the schedule instead`() = runTest {
        assertEquals(listOf("Background import"), marked(sinceLastImport = FOUR_DAYS))
    }

    @Test
    fun `a quiet stretch under three days marks no row at all`() = runTest {
        assertEquals(emptyList(), marked(sinceLastImport = TWO_DAYS))
    }

    @Test
    fun `the last run is reported whatever the platform's rhythm is called`() = runTest {
        viewModel(sinceLastImport = 120_000L, cadence = foregroundOnly()).uiState.test {
            assertEquals("On each app open · no run yet", awaitItem().rows[1].value)
            assertEquals("On each app open · last run 2 min ago", awaitItem().rows[1].value)
        }
    }

    @Test
    fun `a platform that only catches up on open still names where slips come from`() = runTest {
        val rows = viewModel(cadence = foregroundOnly()).uiState.value.rows

        assertEquals("K PLUS album · no background scan on iOS", rows[0].value)
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

    private fun TestScope.marked(sinceLastImport: Long?): List<String> =
        rowsFor(sinceLastImport).filter { it.needsAttention }.map { it.name }

    /**
     * The rows are shared `WhileSubscribed`, so `uiState.value` is the initial value until
     * something collects. Reading it straight would answer for a null journal every time.
     */
    private fun TestScope.rowsFor(
        sinceLastImport: Long? = null,
        cadence: SlipImportCadence = scheduled(),
        settings: ImportSettings = importSettings(),
    ): List<SettingsRowUiModel> {
        val uiState = viewModel(sinceLastImport, cadence, settings).uiState
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
        settings: ImportSettings = importSettings(),
    ) = SettingsViewModel(
        ledger = FakeLedger(sinceLastImport = sinceLastImport),
        settings = settings,
        cadence = cadence,
    )
}
