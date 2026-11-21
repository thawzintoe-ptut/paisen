package com.ptut.expense.play

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.ledger.FakeLedger
import com.ptut.expense.domain.ledger.ObserveTransactionsUseCase
import com.ptut.expense.domain.play.FakePlayBook
import com.ptut.expense.domain.play.ObservePlayChoicesUseCase
import com.ptut.expense.domain.play.ObservePlayOnUseCase
import com.ptut.expense.domain.play.PlanPlayReminderUseCase
import com.ptut.expense.domain.play.PlayGate
import com.ptut.expense.domain.play.PlayReminderWords
import com.ptut.expense.domain.play.SetPlayChoicesUseCase
import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.domain.settings.ObserveAppChoicesUseCase
import com.ptut.expense.domain.settings.PlayChoices
import com.ptut.expense.domain.settings.appSettings
import com.ptut.expense.platform.notify.ReminderPlan
import com.ptut.expense.platform.notify.RepeatReminders
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaySettingsViewModelTest : ViewModelTest() {

    /** Counts what reaches the phone's reminders; a plan that does not change is not handed over twice. */
    private class Counted : RepeatReminders {
        var writes = 0

        override suspend fun replaceAll(plans: List<ReminderPlan>) {
            writes++
        }

        override fun allowed(): Boolean = true
    }

    private fun viewModel(
        settings: AppSettings = appSettings(),
        gate: PlayGate = PlayGate(flagOn = true, debug = true),
        reminders: RepeatReminders? = null,
    ): PlaySettingsViewModel {
        val playOn = ObservePlayOnUseCase(ObserveAppChoicesUseCase(settings), gate)
        return PlaySettingsViewModel(
            observeChoices = ObservePlayChoicesUseCase(ObserveAppChoicesUseCase(settings)),
            setChoices = SetPlayChoicesUseCase(settings),
            playOn = playOn,
            planReminder = reminders?.let {
                PlanPlayReminderUseCase(
                    reminders = it,
                    playOn = playOn,
                    transactions = ObserveTransactionsUseCase(FakeLedger()),
                    book = FakePlayBook(),
                    words = object : PlayReminderWords {
                        override val title = "PaiSeN"
                        override val body = "Guess first?"
                    },
                )
            },
        )
    }

    private fun TestScope.latest(state: StateFlow<PlaySettingsUiState>): PlaySettingsUiState {
        backgroundScope.launch { state.collect {} }
        runCurrent()
        return state.value
    }

    @Test
    fun `the master on shows its three groups and the footer`() = runTest {
        val state = latest(viewModel().uiState)

        assertTrue(state.master.on)
        assertEquals("Stamps, guesses and challenges are on", state.master.line)
        assertEquals(listOf("On Today", "In shared groups", "Sharing and reminders", "Help and tips"), state.groups.map { it.title })
        assertEquals("Your own play is worked out on this phone and never leaves it.", state.footer)
    }

    @Test
    fun `the master off hides every group`() = runTest {
        val state = latest(viewModel(appSettings(AppChoices(play = PlayChoices(on = false)))).uiState)

        assertFalse(state.master.on)
        assertEquals("Off: PaiSeN shows money only", state.master.line)
        assertTrue(state.groups.isEmpty())
        assertNull(state.footer)
    }

    @Test
    fun `the switches ship with amounts and the reminder off`() = runTest {
        val rows = latest(viewModel().uiState).groups.flatMap { it.rows }.associate { it.id to it.on }

        assertEquals(
            mapOf(
                PlaySetting.Run to true,
                PlaySetting.Guess to true,
                PlaySetting.Challenges to true,
                PlaySetting.GroupStamps to true,
                PlaySetting.ShareAmounts to false,
                PlaySetting.WeeklyReminder to false,
                PlaySetting.Tips to true,
            ),
            rows,
        )
    }

    @Test
    fun `each switch writes its own choice`() = runTest {
        val settings = appSettings()
        val play = viewModel(settings)

        play.set(PlaySetting.Guess, false)
        play.set(PlaySetting.ShareAmounts, true)
        play.set(PlaySetting.Master, false)

        assertEquals(PlayChoices(on = false, guess = false, shareAmounts = true), settings.choices.value.play)
    }

    @Test
    fun `a release build shows the rows of the parts it offers`() = runTest {
        // Every part passed its phone check (D17), so a release build lists them as debug does.
        val state = latest(viewModel(gate = PlayGate(flagOn = true, debug = false)).uiState)

        assertTrue(state.groups.isNotEmpty())
    }

    @Test
    fun `turning guess off plans the reminder again`() = runTest {
        val reminders = Counted()
        val play = viewModel(reminders = reminders)

        play.set(PlaySetting.Guess, false)
        runCurrent()

        assertEquals(1, reminders.writes)
    }

    @Test
    fun `a switch the reminder does not read leaves it alone`() = runTest {
        val reminders = Counted()
        val play = viewModel(reminders = reminders)

        play.set(PlaySetting.Run, false)
        play.set(PlaySetting.ShareAmounts, true)
        runCurrent()

        assertEquals(0, reminders.writes)
    }

    @Test
    fun `see all tips again clears the seen tips and turns tips back on`() = runTest {
        val settings = appSettings(AppChoices(play = PlayChoices(tips = false, seenTips = setOf("Run", "Stamp"))))
        val viewModel = viewModel(settings)

        viewModel.showTipsAgain()

        assertTrue(settings.choices.value.play.tips)
        assertTrue(settings.choices.value.play.seenTips.isEmpty())
        assertEquals("See all tips again", latest(viewModel.uiState).tipsAgain?.name)
    }
}
