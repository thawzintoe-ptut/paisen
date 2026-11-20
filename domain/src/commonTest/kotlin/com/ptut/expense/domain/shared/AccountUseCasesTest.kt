package com.ptut.expense.domain.shared

import com.ptut.expense.sync.ChangesWaiting
import com.ptut.expense.sync.auth.Account
import com.ptut.expense.sync.auth.AuthRepository
import com.ptut.expense.sync.auth.ProviderToken
import com.ptut.expense.sync.auth.SignInCancelled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AccountUseCasesTest {

    private val token = ProviderToken(providerId = "google.com", idToken = "id-token")

    @Test
    fun aTokenFirebaseTakesSignsIn() = runTest {
        assertEquals(SignInResult.SignedIn, SignInUseCase(FakeAuth())(obtain = { token }))
    }

    @Test
    fun aClosedSheetOrNoSignInIsCancelledAndSaysNothing() = runTest {
        assertEquals(SignInResult.Cancelled, SignInUseCase(FakeAuth())(obtain = { throw SignInCancelled() }))
        assertEquals(SignInResult.Cancelled, SignInUseCase(auth = null)(obtain = { token }))
    }

    @Test
    fun aSignInWithNoAnswerReadsAsOffline() = runTest {
        assertEquals(SignInResult.Offline, SignInUseCase(FakeAuth(failure = IllegalStateException("no answer")))(obtain = { token }))
    }

    @Test
    fun aCancelledSignInIsNotCountedAsAFailure() = runTest {
        assertFailsWith<CancellationException> {
            SignInUseCase(FakeAuth(failure = CancellationException("screen closed")))(obtain = { token })
        }
    }

    @Test
    fun signingOutOrDeletingWithNothingInTheWayIsDone() = runTest {
        val auth = FakeAuth()

        assertEquals(AccountExit.Done, SignOutUseCase(auth)())
        assertEquals(AccountExit.Done, DeleteAccountUseCase(auth)())
        assertEquals(listOf("signOut", "deleteAccount"), auth.calls)
    }

    @Test
    fun changesNotYetSyncedStopTheExitAndSayHowMany() = runTest {
        val auth = FakeAuth(failure = ChangesWaiting(3))

        assertEquals(AccountExit.Waiting(3), SignOutUseCase(auth)())
        assertEquals(AccountExit.Waiting(3), DeleteAccountUseCase(auth)())
    }

    @Test
    fun anyOtherExitFailureKeepsItsCause() = runTest {
        val failure = IllegalStateException("no answer")

        val exit = SignOutUseCase(FakeAuth(failure = failure))()

        assertEquals(failure, assertIs<AccountExit.Failed>(exit).cause)
    }

    @Test
    fun aCancelledExitIsNotCountedAsAFailure() = runTest {
        assertFailsWith<CancellationException> { DeleteAccountUseCase(FakeAuth(failure = CancellationException("screen closed")))() }
    }

    private class FakeAuth(private val failure: Exception? = null) : AuthRepository {
        val calls = mutableListOf<String>()

        override val available = true

        override val account = MutableStateFlow<Account>(Account.SignedOut)

        override suspend fun signIn(token: ProviderToken): Account.SignedIn {
            failure?.let { throw it }
            return Account.SignedIn(uid = "uid-1", name = "Ko Ko", email = null, provider = token.providerId)
        }

        override suspend fun idToken(): String = "firebase-token"

        override suspend fun signOut() {
            calls += "signOut"
            failure?.let { throw it }
        }

        override suspend fun deleteAccount() {
            calls += "deleteAccount"
            failure?.let { throw it }
        }
    }
}
