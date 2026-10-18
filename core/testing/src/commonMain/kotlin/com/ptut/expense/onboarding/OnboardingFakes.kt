package com.ptut.expense.onboarding

import com.ptut.expense.platform.photos.PhotoAccessRequest
import com.ptut.expense.platform.photos.PhotoSettings
import kotlinx.coroutines.CompletableDeferred

/** First run's flag, in memory. The seed is what puts a test either side of the answer. */
class FakeOnboardingFlag(private var finished: Boolean = false) : OnboardingFlag {

    var writes = 0
        private set

    override fun hasFinished(): Boolean = finished

    override fun markFinished() {
        finished = true
        writes++
    }
}

/**
 * The system photo dialog, without one. It closes as soon as it is raised, because that is what
 * every test but one is asking about — [holdOpen] is for the one that asks what the screen does
 * while the dialog is still up.
 */
class FakePhotoAccessRequest : PhotoAccessRequest {

    private var dialog = CompletableDeferred(Unit)

    var asks = 0
        private set

    fun holdOpen() {
        dialog = CompletableDeferred()
    }

    fun answer() {
        dialog.complete(Unit)
    }

    override suspend fun ask() {
        asks++
        dialog.await()
    }
}

/** Today's Fix page, without the system one. [opens] counts the taps that would have left the app. */
class FakePhotoSettings(override val steps: String = "Tap Photos, then Allow all") : PhotoSettings {

    var opens = 0
        private set

    override fun open() {
        opens++
    }
}
