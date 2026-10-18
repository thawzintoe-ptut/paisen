package com.ptut.expense.platform.photos

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * FR-2 on Android, through the runtime permission dialog.
 *
 * The dialog needs an `ActivityResultLauncher`, which can only be registered from inside the
 * Activity and which this has no business holding. So it asks over [asks] and waits, and [settle]
 * is how the answer comes back — the same handshake
 * [com.ptut.expense.platform.files.SafLedgerFileStore] uses for its two system pickers, and for the
 * same reason: the pending answer lives here rather than in the composable that launches, so a
 * rotation mid-dialog cannot lose it.
 */
class LauncherPhotoAccessRequest : PhotoAccessRequest {

    /**
     * Buffered, like the file store's: a request must not be dropped because the host had not
     * started collecting yet, which would leave first run's button waiting on nobody.
     */
    private val requests = Channel<Unit>(Channel.BUFFERED)

    val asks: Flow<Unit> = requests.receiveAsFlow()

    private var answer: CompletableDeferred<Unit>? = null

    /** Called by the host when the dialog closes, whichever way it was answered. */
    fun settle() {
        answer?.complete(Unit)
        answer = null
    }

    override suspend fun ask() {
        val pending = CompletableDeferred<Unit>()
        answer = pending
        requests.send(Unit)
        pending.await()
    }
}
