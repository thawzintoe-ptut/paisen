package com.ptut.expense.sync

import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.auth.AuthException

/**
 * #195: what went wrong, in the kinds a person can do something about. [NeedsFreshSignIn]: Firebase
 * deletes an account only after a recent sign-in, so the person signs in once more.
 */
enum class SyncProblem { Offline, Refused, SessionEnded, NeedsFreshSignIn }

fun Throwable.syncProblem(): SyncProblem = when (this) {
    is FirestoreException -> when {
        status == FirestoreStatus.NO_ANSWER -> SyncProblem.Offline
        status == FirestoreStatus.UNAUTHORIZED -> SyncProblem.SessionEnded
        FirestoreStatus.isPermanent(status) -> SyncProblem.Refused
        else -> SyncProblem.Offline
    }

    is AuthException -> when {
        needsFreshSignIn -> SyncProblem.NeedsFreshSignIn
        sessionEnded -> SyncProblem.SessionEnded
        else -> SyncProblem.Offline
    }

    else -> SyncProblem.Offline
}

/** The line a screen shows for [syncProblem]. A [JoinException] keeps its own words. */
fun Throwable.syncMessage(): String = (this as? JoinException)?.message ?: when (syncProblem()) {
    SyncProblem.Offline -> AppText.copy.shared.sync.offline
    SyncProblem.Refused -> AppText.copy.shared.sync.refused
    SyncProblem.SessionEnded, SyncProblem.NeedsFreshSignIn -> AppText.copy.shared.sync.sessionEnded
}
