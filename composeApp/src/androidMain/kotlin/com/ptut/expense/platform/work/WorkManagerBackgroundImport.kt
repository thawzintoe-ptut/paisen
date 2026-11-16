package com.ptut.expense.platform.work

import android.content.Context
import com.ptut.expense.platform.photos.PhotoAccessLevel
import com.ptut.expense.platform.photos.photoAccessLevel

/**
 * [BackgroundImport] over WorkManager, which is the only thing on Android that can run this app
 * while it is closed.
 *
 * `applicationContext`, because the schedule outlives every Activity and a held one would be
 * leaked for the life of the process.
 */
class WorkManagerBackgroundImport(context: Context) : BackgroundImport {

    private val application = context.applicationContext

    /**
     * Scans once either way, and only schedules where a scan can succeed.
     *
     * A periodic job without photo access would fail every fifteen minutes for as long as the app
     * is installed — the same reason the app-open path refuses to schedule one. The single run
     * still goes, because that failure is what writes the journal line Today's banner reads and
     * what names the fix. "Select photos" gets the single run too: nothing new appears while closed.
     */
    override fun start() {
        if (application.photoAccessLevel() == PhotoAccessLevel.All) {
            application.startSlipImport()
        } else {
            application.scanForSlipsNow()
        }
    }

    override fun stop() = application.stopSlipImport()
}
