package com.ptut.expense.settings

import app.cash.turbine.test
import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.settings.ImportChoices
import com.ptut.expense.domain.settings.ImportSettings
import com.ptut.expense.domain.settings.importSettings
import com.ptut.expense.importer.FakeSlipSource
import com.ptut.expense.importer.FakeSlipTextReader
import com.ptut.expense.importer.InMemoryImportStore
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.platform.work.BackgroundImport
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackgroundImportViewModelTest : ViewModelTest() {

    private val toasts = ToastMessages()

    @Test
    fun `switching off writes the choice and cancels the job`() = runTest {
        val settings = importSettings()
        val schedule = RecordingSchedule()

        viewModel(settings = settings, background = schedule).setRunning(false)

        assertFalse(settings.choices.value.backgroundImport)
        assertEquals(listOf("stop"), schedule.calls)
    }

    /** A switch that changes nothing visible for fifteen minutes reads as one that did not work. */
    @Test
    fun `switching on schedules and scans at once`() = runTest {
        val settings = importSettings(ImportChoices(backgroundImport = false))
        val schedule = RecordingSchedule()

        viewModel(settings = settings, background = schedule).setRunning(true)

        assertTrue(settings.choices.value.backgroundImport)
        assertEquals(listOf("start"), schedule.calls)
    }

    /** C-4: nothing to start on iOS, and the switch must not throw where nothing is bound. */
    @Test
    fun `a platform with no schedule still records the choice`() = runTest {
        val settings = importSettings()

        viewModel(settings = settings, background = null).setRunning(false)

        assertFalse(settings.choices.value.backgroundImport)
    }

    @Test
    fun `a platform with no schedule draws no switch and says why`() = runTest {
        val state = viewModel(cadence = foregroundOnly()).uiState.value

        assertFalse(state.adjustable, "a switch here would turn off a job that does not exist")
        assertEquals("no window while the app is closed", state.explanation)
    }

    @Test
    fun `a ledger nothing has imported into says so rather than naming a span`() = runTest {
        assertEquals("Nothing has been imported yet", viewModel().uiState.value.lastRun)
    }

    @Test
    fun `the last run is named as a span`() = runTest {
        viewModel(sinceLastImport = 120_000L).uiState.test {
            // The shared flow hands back its initial value first, then the ledger's own answer.
            assertEquals("Nothing has been imported yet", awaitItem().lastRun)
            assertEquals("Last run 2 min ago", awaitItem().lastRun)
        }
    }

    @Test
    fun `a scan that found nothing is reported as nothing rather than as a zero`() = runTest {
        val settings = viewModel()

        toasts.messages.test {
            settings.checkNow()
            runCurrent()

            assertEquals("No new slips in your folders", awaitItem().text)
        }
    }

    @Test
    fun `a scan that filed a slip says what it did`() = runTest {
        val source = FakeSlipSource().apply { add("slip.png", READABLE, capturedAtEpochMillis = 1L) }
        val settings = viewModel(importer = importer(source))

        toasts.messages.test {
            settings.checkNow()
            runCurrent()

            assertTrue(awaitItem().text.startsWith("Found 1 · filed"), "the run's counts")
        }
    }

    @Test
    fun `a folder that cannot be read refuses in the error tone`() = runTest {
        val source = FakeSlipSource().apply { scanFails = true }
        val settings = viewModel(importer = importer(source))

        toasts.messages.test {
            settings.checkNow()
            runCurrent()

            val toast = awaitItem()
            assertEquals(ToastTone.Error, toast.tone)
            assertTrue(toast.text.contains("Activity"), toast.text)
        }
    }

    /** The button is disabled while a scan is out, and a second tap must not queue a second run. */
    @Test
    fun `a second tap while a scan is running is ignored`() = runTest {
        val source = FakeSlipSource()
        val store = InMemoryImportStore()
        val settings = viewModel(importer = SlipImporter(source, FakeSlipTextReader(), store))

        settings.checkNow()
        settings.checkNow()
        runCurrent()

        assertEquals(1, store.runs.size)
    }

    private fun importer(source: FakeSlipSource) =
        SlipImporter(source, FakeSlipTextReader(), InMemoryImportStore())

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
        settings: ImportSettings = importSettings(),
        importer: SlipImporter = importer(FakeSlipSource()),
        cadence: SlipImportCadence = scheduled(),
        background: BackgroundImport? = RecordingSchedule(),
    ) = BackgroundImportViewModel(
        ledger = FakeLedger(sinceLastImport = sinceLastImport),
        settings = settings,
        importer = importer,
        toasts = toasts,
        cadence = cadence,
        background = background,
    )
}

private class RecordingSchedule : BackgroundImport {

    val calls = mutableListOf<String>()

    override fun start() {
        calls += "start"
    }

    override fun stop() {
        calls += "stop"
    }
}

/** One readable slip, enough for the importer to file a row and report a count. */
private val READABLE = """
    K PLUS
    Transfer successful
    Amount 120.00 THB
    To Foodland Sukhumvit
    Ref 0142K9
    14 Aug 26 19:41
""".trimIndent()
