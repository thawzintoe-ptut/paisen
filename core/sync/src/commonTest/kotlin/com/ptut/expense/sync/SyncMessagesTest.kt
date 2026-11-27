package com.ptut.expense.sync

import com.ptut.expense.sync.auth.AuthException
import com.ptut.expense.sync.auth.authFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncMessagesTest {

    @Test
    fun firebaseAskingForARecentSignInIsItsOwnKind() {
        val failure = authFailure(400, "CREDENTIAL_TOO_OLD_LOGIN_AGAIN")

        assertEquals(SyncProblem.NeedsFreshSignIn, failure.syncProblem())
    }

    @Test
    fun aTimeoutARateLimitOrAnAbortedCommitIsOfflineAndMayPassLater() {
        listOf(408, 409, 429, 500, FirestoreStatus.NO_ANSWER).forEach { status ->
            assertEquals(SyncProblem.Offline, FirestoreException(status, "").syncProblem(), "status $status")
            assertFalse(FirestoreStatus.isPermanent(status))
        }
    }

    @Test
    fun anotherClientErrorIsRefusedForGood() {
        listOf(400, 403, 404).forEach { status ->
            assertEquals(SyncProblem.Refused, FirestoreException(status, "").syncProblem(), "status $status")
            assertTrue(FirestoreStatus.isPermanent(status))
        }
        assertEquals(SyncProblem.SessionEnded, FirestoreException(401, "").syncProblem())
    }

    @Test
    fun anEndedSessionStaysAnEndedSession() {
        val failure = AuthException("Firebase Auth said 400: TOKEN_EXPIRED", sessionEnded = true)

        assertEquals(SyncProblem.SessionEnded, failure.syncProblem())
    }
}
