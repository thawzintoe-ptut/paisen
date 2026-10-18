package com.ptut.expense.shared

import com.ptut.expense.sync.SharedSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Pulls every [every] for as long as it is collected, which through `WhileSubscribed` means while
 * the screen shows. A tab left behind or an app in the background costs no reads. It emits before
 * the first pull, so a `combine` on it waits for no network.
 */
internal fun syncPolling(sync: SharedSync, runner: RepeatRunner?, every: Duration): Flow<Unit> = flow {
    emit(Unit)
    while (true) {
        // #203: a group deleted for everyone has gone from this phone, so its repeat reminders are rebuilt.
        val removedAny = runCatching { sync.syncAll() }.getOrDefault(false)
        if (removedAny) runCatching { runner?.run() }
        // #218: a Sign in from the signed-out line syncs at once instead of at the next tick.
        withTimeoutOrNull(every) { sync.signedIn.drop(1).first { it } }
    }
}
