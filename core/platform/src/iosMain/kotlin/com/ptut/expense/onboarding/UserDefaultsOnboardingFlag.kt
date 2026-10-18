package com.ptut.expense.onboarding

import platform.Foundation.NSUserDefaults

private const val FINISHED = "ptut-first-run-finished"

/** One boolean. `boolForKey` answers false for a key nobody has written, which is a fresh install. */
class UserDefaultsOnboardingFlag : OnboardingFlag {

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun hasFinished(): Boolean = defaults.boolForKey(FINISHED)

    override fun markFinished() = defaults.setBool(true, FINISHED)
}
