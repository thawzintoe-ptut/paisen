package com.ptut.expense.play

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.play.ObservePlayOnUseCase
import com.ptut.expense.domain.play.PlayGate
import com.ptut.expense.domain.play.PlayTip
import com.ptut.expense.domain.play.SetPlayChoicesUseCase
import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.domain.settings.ObserveAppChoicesUseCase
import com.ptut.expense.domain.settings.PlayChoices
import com.ptut.expense.domain.settings.appSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlayTipViewModelTest : ViewModelTest() {

    private fun TestScope.tip(
        tip: PlayTip,
        settings: AppSettings = appSettings(),
        gate: PlayGate = PlayGate(flagOn = true, debug = true),
    ): PlayTipViewModel {
        val viewModel = PlayTipViewModel(tip, ObservePlayOnUseCase(ObserveAppChoicesUseCase(settings), gate), SetPlayChoicesUseCase(settings))
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `a tip shows once with its words and Got it hides it for good`() = runTest {
        val settings = appSettings()
        val run = tip(PlayTip.Run, settings)

        val shown = assertNotNull(run.uiState.value)
        assertEquals("Tip · 1 of 6", shown.eyebrow)
        assertEquals("Your tray clear run", shown.title)

        run.gotIt()
        runCurrent()

        assertNull(run.uiState.value)
        assertEquals(setOf("Run"), settings.choices.value.play.seenTips)
    }

    @Test
    fun `the question tip waits until the run tip is seen, so Today shows one at a time`() = runTest {
        assertNull(tip(PlayTip.Question).uiState.value)
        assertNotNull(tip(PlayTip.Question, appSettings(AppChoices(play = PlayChoices(seenTips = setOf("Run"))))).uiState.value)
    }

    @Test
    fun `Skip all tips turns every tip off`() = runTest {
        val settings = appSettings()
        val stamp = tip(PlayTip.Stamp, settings)

        stamp.skipAll()
        runCurrent()

        assertFalse(settings.choices.value.play.tips)
        assertNull(stamp.uiState.value)
        assertNull(tip(PlayTip.Wheel, settings).uiState.value)
    }

    @Test
    fun `no tip shows while play is off or the tips flag is off`() = runTest {
        assertNull(tip(PlayTip.Jar, appSettings(AppChoices(play = PlayChoices(on = false)))).uiState.value)
        assertNull(tip(PlayTip.Jar, gate = PlayGate(flagOn = true, debug = true, tipsOn = false)).uiState.value)
    }
}
