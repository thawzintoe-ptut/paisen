package com.ptut.expense.platform.work

import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.locale.AppText
import com.ptut.expense.onboarding.Onboarding
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification

/**
 * FR-3 on iOS: the catch-up that happens when the app comes back.
 *
 * The twin of Android's `SlipImportSchedule`, and deliberately not a port of it. iOS hands an app
 * no periodic window it can rely on, so there is no fifteen-minute job to start and no switch in
 * Settings that could turn one off — C-4, which is why the Settings row states a fact.
 *
 * Coming back to the foreground is the only moment this app is certainly allowed to work, so it is
 * the moment the album is read. Whatever K PLUS wrote while the app was away is found then.
 */
class SlipImportSchedule(
    private val importer: SlipImporter,
    private val onboarding: Onboarding,
    private val toasts: ToastMessages,
    private val scope: CoroutineScope,
) {

    private var watching = false

    /**
     * Waits for first run, then watches, and scans once straight away.
     *
     * Nothing is read before first run is answered: #19 gives the photo dialog a screen that
     * explains it, and `PHPhotoLibrary` raised behind that screen would ask the question before the
     * screen got to. Waiting on the answer rather than reading it once is what starts the album
     * watch the moment the reader allows, instead of on the launch after.
     *
     * The first activation notification may arrive before or after the entry point builds the UI,
     * so both paths run. A redundant run costs almost nothing: [SlipImporter] holds a lock, and the
     * second caller finds an album the first one has already read through.
     */
    fun start() {
        if (watching) return
        watching = true

        scope.launch {
            onboarding.isFinished.first { answered -> answered }

            NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { scanNow() }

            scanNow()
        }
    }

    /** One run, now — the app came back, or a slip was shared into it. */
    fun scanNow() {
        scope.launch {
            try {
                // No dialog here. First run owns the one ask, and a refusal is answered by the
                // journal rather than by a second dialog on every foreground.
                importer.run()
            } catch (failure: Exception) {
                // The importer journals a scan it could not start. Anything that gets past it is
                // the ledger itself failing, and the app is in the foreground by definition here,
                // so the reader is told rather than left with a green banner over a dead import.
                toasts.show(
                    ToastMessage(
                        text = failure.message ?: AppText.copy.slips.importCouldNotRun,
                        tone = ToastTone.Error,
                    ),
                )
            }
        }
    }
}
