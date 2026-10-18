package com.ptut.expense.onboarding

import android.content.Context

private const val MARKER_FILE = "ptut-first-run"
private const val FINISHED = "finished"

/** One boolean, in its own file, so clearing it by hand is a way to see first run again. */
class SharedPreferencesOnboardingFlag(context: Context) : OnboardingFlag {

    private val marker = context.applicationContext
        .getSharedPreferences(MARKER_FILE, Context.MODE_PRIVATE)

    override fun hasFinished(): Boolean = marker.getBoolean(FINISHED, false)

    override fun markFinished() = marker.edit().putBoolean(FINISHED, true).apply()
}
