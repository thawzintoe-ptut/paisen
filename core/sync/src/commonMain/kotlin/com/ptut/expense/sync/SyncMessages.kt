package com.ptut.expense.sync

import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.auth.AuthException

/** #195: what went wrong, in the three kinds a person can do something about. */
enum class SyncProblem { Offline, Refused, SessionEnded }

fun Throwable.syncProblem(): SyncProblem = when (this) {
    is FirestoreException -> when (status) {
        0 -> SyncProblem.Offline
        UNAUTHORIZED -> SyncProblem.SessionEnded
        in CLIENT_ERRORS -> SyncProblem.Refused
        else -> SyncProblem.Offline
    }

    is AuthException -> if (sessionEnded) SyncProblem.SessionEnded else SyncProblem.Offline

    else -> SyncProblem.Offline
}

/** The line a screen shows for [syncProblem]. A [JoinException] keeps its own words. */
fun Throwable.syncMessage(): String = (this as? JoinException)?.message ?: when (syncProblem()) {
    SyncProblem.Offline -> AppText.copy.shared.sync.offline
    SyncProblem.Refused -> AppText.copy.shared.sync.refused
    SyncProblem.SessionEnded -> AppText.copy.shared.sync.sessionEnded
}

private const val UNAUTHORIZED = 401
private val CLIENT_ERRORS = 400..499
