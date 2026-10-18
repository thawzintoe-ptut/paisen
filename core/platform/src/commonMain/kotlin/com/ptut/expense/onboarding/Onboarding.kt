package com.ptut.expense.onboarding

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the first-run answer survives a process death. `SharedPreferences` on Android,
 * `NSUserDefaults` on iOS — one boolean either way, which is the whole reason [Onboarding] is
 * common.
 *
 * Read synchronously and not through a suspend call on purpose: the shell has to know which screen
 * to draw on the first frame, and an awaited answer would show Today for a moment on a phone that
 * has never opened the app.
 */
interface OnboardingFlag {

    /** False on a fresh install, which is what puts first run in front of Today. */
    fun hasFinished(): Boolean

    fun markFinished()
}

/**
 * §4: this app has one user and no accounts, so first run replaces sign-in. It is the only screen
 * that asks for anything, and this is what remembers that it was answered.
 *
 * A `single` holding a flow rather than a bare getter, because three callers need the same answer
 * and one of them writes it: the shell chooses between first run and the ledger, [OnboardingViewModel]
 * finishes it, and each platform's import trigger waits for it — FR-2's photo dialog belongs to the
 * "Allow photo access" button, so nothing may scan before that button has been offered.
 *
 * Answered means answered, whichever way. Allowing and declining both end first run, because the
 * screen's promise is that expenses can still be added by hand.
 */
class Onboarding(private val flag: OnboardingFlag) {

    private val finished = MutableStateFlow(flag.hasFinished())

    val isFinished: StateFlow<Boolean> = finished.asStateFlow()

    fun finish() {
        if (finished.value) return
        // Written before it is announced. If the process dies between the two the reader answers
        // first run once more, which is the harmless way round — landing on a Today they never
        // agreed to is not.
        flag.markFinished()
        finished.value = true
    }
}
