package com.ptut.expense.domain.play

import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.settings.ObserveAppChoicesUseCase
import com.ptut.expense.domain.settings.PlayChoices
import com.ptut.expense.domain.settings.appSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObservePlayOnUseCaseTest {

    private val debug = PlayGate(flagOn = true, debug = true)

    private fun on(play: PlayChoices = PlayChoices(), gate: PlayGate = debug) =
        ObservePlayOnUseCase(ObserveAppChoicesUseCase(appSettings(AppChoices(play = play))), gate)

    @Test
    fun `a debug build with play on shows every part but the reminder which ships off`() = runTest {
        PlayPart.entries.forEach { part -> assertEquals(part != PlayPart.Reminder, on().invoke(part).first(), "$part") }
    }

    @Test
    fun `the master switch turns every part off`() = runTest {
        PlayPart.entries.forEach { part -> assertFalse(on(PlayChoices(on = false)).invoke(part).first(), "$part") }
    }

    @Test
    fun `a sub-switch turns only its own parts off`() = runTest {
        val use = on(PlayChoices(guess = false))

        assertFalse(use(PlayPart.Guess).first())
        assertTrue(use(PlayPart.Run).first())
    }

    @Test
    fun `the run switch holds the stamps and the question with it`() = runTest {
        val use = on(PlayChoices(run = false))

        listOf(PlayPart.Run, PlayPart.Stamps, PlayPart.Question).forEach { assertFalse(use(it).first(), "$it") }
        assertTrue(use(PlayPart.Guess).first())
    }

    @Test
    fun `Remote Config off hides play whatever the switches say`() = runTest {
        assertFalse(on(gate = PlayGate(flagOn = false, debug = true)).invoke(PlayPart.Run).first())
    }

    @Test
    fun `a release build hides a part not yet checked on a phone`() = runTest {
        val release = on(gate = PlayGate(flagOn = true, debug = false))

        assertFalse(release(PlayPart.Run).first())
        assertFalse(release.offered(PlayPart.Run))
    }
}
