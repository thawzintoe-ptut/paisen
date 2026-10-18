package com.ptut.expense.platform.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * C-5: fifteen minutes is the platform's floor, not a preference. Asking for less does not get
 * less, and OEM battery managers stretch it further — which is why the Settings row says "near
 * real time" and no copy anywhere promises instant.
 */
private const val SCAN_INTERVAL_MINUTES = 15L

private const val PERIODIC_SCAN = "ptut-slip-import-periodic"
private const val SCAN_NOW = "ptut-slip-import-now"

/**
 * Starts watching the K PLUS folder, and scans it once straight away.
 *
 * Called from the app rather than the Application, because a scan without photo access only writes
 * a failed run to the journal. `UPDATE` rather than `KEEP` so a changed interval takes effect on
 * the next launch instead of surviving until the app is reinstalled.
 *
 * No `Constraints`. Every constraint WorkManager offers is about a resource this run does not use,
 * and a network constraint on an app that makes no request would only delay it forever.
 */
fun Context.startSlipImport() {
    watchForSlips()
    scanForSlipsNow()
}

/** The fifteen-minute job alone. The app scans on its own each time it comes back. */
fun Context.watchForSlips() {
    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        PERIODIC_SCAN,
        ExistingPeriodicWorkPolicy.UPDATE,
        PeriodicWorkRequestBuilder<SlipImportWorker>(SCAN_INTERVAL_MINUTES, TimeUnit.MINUTES).build(),
    )
}

/**
 * One run, now — the app was opened, or a slip was shared into it.
 *
 * `APPEND_OR_REPLACE` rather than `KEEP`: a slip shared while a scan is already running has to get
 * its own run, or it waits fifteen minutes for the next one. A redundant run costs almost nothing,
 * because the scan marker leaves it with nothing to read.
 */
fun Context.scanForSlipsNow() {
    WorkManager.getInstance(this).enqueueUniqueWork(
        SCAN_NOW,
        ExistingWorkPolicy.APPEND_OR_REPLACE,
        OneTimeWorkRequestBuilder<SlipImportWorker>().build(),
    )
}

/**
 * Stops watching. The reader turned the schedule off, so the periodic job goes and nothing runs
 * while the app is closed.
 *
 * Only the periodic work is cancelled. [scanForSlipsNow] is what an opened app and a shared slip
 * ask for, and neither is background work — switching the schedule off must not stop the app
 * catching up on the slips already sitting in the folder while the reader is looking at it.
 */
fun Context.stopSlipImport() {
    WorkManager.getInstance(this).cancelUniqueWork(PERIODIC_SCAN)
}
