package com.ptut.expense.sync

import com.ptut.expense.data.local.PtutDatabaseContext
import platform.Foundation.NSUserDefaults

internal actual fun endedHereNotice(context: PtutDatabaseContext): EndedHereNotice = UserDefaultsEndedHereNotice

private object UserDefaultsEndedHereNotice : EndedHereNotice {
    private const val KEY = "ptut-ended-here"

    override fun read(): Boolean = NSUserDefaults.standardUserDefaults.boolForKey(KEY)

    override fun write(pending: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(pending, forKey = KEY)
    }
}
