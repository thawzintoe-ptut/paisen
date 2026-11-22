package com.ptut.expense.sync

import android.content.Context
import com.ptut.expense.data.local.PtutDatabaseContext

internal actual fun endedHereNotice(context: PtutDatabaseContext): EndedHereNotice = PreferencesEndedHereNotice(context.applicationContext)

private class PreferencesEndedHereNotice(context: Context) : EndedHereNotice {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read(): Boolean = prefs.getBoolean(KEY, false)

    // Committed at once: the process may die right after the sign-out that follows.
    override fun write(pending: Boolean) {
        prefs.edit().putBoolean(KEY, pending).commit()
    }

    private companion object {
        const val FILE = "session_notice"
        const val KEY = "ended_here"
    }
}
