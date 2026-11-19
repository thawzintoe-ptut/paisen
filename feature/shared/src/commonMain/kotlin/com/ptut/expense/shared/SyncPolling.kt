package com.ptut.expense.shared

import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.sync.SyncOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** The longest a failing poll waits between tries, however long it has been failing. */
internal val MAX_POLL_WAIT: Duration = 5.minutes

/**
 * Runs [pull] every [every] for as long as it is collected, which through `WhileSubscribed` means
 * while the screen shows. A tab left behind or an app in the background costs no reads. It emits
 * before the first pull, so a `combine` on it waits for no network. [pull] answers as
 * `SharedSync.syncAllOutcome` does, and may also throw when the sync failed.
 *
 * Each failed pull doubles the wait, up to [MAX_POLL_WAIT], so a phone offline for an hour does not
 * try four times a minute; the first pull that works brings the wait back to [every].
 */
internal fun syncPolling(signedIn: Flow<Boolean>, runner: RepeatRunner?, every: Duration, pull: suspend () -> SyncOutcome): Flow<Unit> = flow {
    emit(Unit)
    val longest = maxOf(every, MAX_POLL_WAIT)
    var wait = every
    while (true) {
        wait = if (syncOnce(runner, pull)) every else minOf(wait * 2, longest)
        // #218: a Sign in from the signed-out line syncs at once instead of at the next tick.
        withTimeoutOrNull(wait) { signedIn.drop(1).first { it } }
    }
}

/** One sync, as a tick or a pull-to-refresh runs it. False when [pull] failed; it waits for the next one. */
internal suspend fun syncOnce(runner: RepeatRunner?, pull: suspend () -> SyncOutcome): Boolean {
    val outcome = runSuspendCatching { pull() }.getOrNull()
    // #203: a group deleted for everyone has gone from this phone, so its repeat reminders are rebuilt.
    if (outcome?.groupsChanged == true) runSuspendCatching { runner?.run() }
    return outcome != null && !outcome.failed
}
