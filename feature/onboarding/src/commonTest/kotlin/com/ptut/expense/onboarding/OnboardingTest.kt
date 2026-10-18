package com.ptut.expense.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingTest {

    @Test
    fun `a fresh install has not been through first run`() {
        assertFalse(Onboarding(FakeOnboardingFlag()).isFinished.value)
    }

    /**
     * Read on construction rather than awaited. The shell picks its screen on the first frame, and
     * an answer that arrives later would show Today for a moment on a phone that has never opened
     * the app.
     */
    @Test
    fun `an install that has already answered opens finished`() {
        assertTrue(Onboarding(FakeOnboardingFlag(finished = true)).isFinished.value)
    }

    @Test
    fun `finishing is written down as well as announced`() {
        val flag = FakeOnboardingFlag()
        val onboarding = Onboarding(flag)

        onboarding.finish()

        assertTrue(onboarding.isFinished.value)
        assertEquals(1, flag.writes, "the answer did not reach the flag")
    }

    /**
     * Both exits call this, and on Android the dialog's answer and the ViewModel's own cleanup can
     * both arrive. A second write is harmless but a second announcement is not — it would recompose
     * the shell for nothing.
     */
    @Test
    fun `finishing twice writes once`() {
        val flag = FakeOnboardingFlag()
        val onboarding = Onboarding(flag)

        onboarding.finish()
        onboarding.finish()

        assertEquals(1, flag.writes)
    }
}
