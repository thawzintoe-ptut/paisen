package com.ptut.expense.sync.auth

import com.ptut.expense.data.local.entity.SharedGroupEntity
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.sync.FakeFirestore
import com.ptut.expense.sync.FakeSharedDao
import com.ptut.expense.sync.FakeSharedRepository
import com.ptut.expense.sync.SharedSync
import com.ptut.expense.sync.SyncHealth
import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryTest {

    private val store = MemoryStore("""{"uid":"uid-1","refreshToken":"r1","name":"Mali","provider":"google.com"}""")

    /** Every request fails before it leaves the phone, as it does with no network. */
    private val offline = HttpClient {
        install(createClientPlugin("Offline") { onRequest { _, _ -> throw IllegalStateException("Unable to resolve host") } })
    }

    @Test
    fun `an offline refresh fails as a sign-in that got no answer and keeps the session`() = runTest {
        val auth = FirebaseAuthRepository(FirebaseAuthApi(offline, "key"), store, available = true)

        val failure = assertFailsWith<AuthException> { auth.idToken() }

        assertFalse(failure.sessionEnded)
        assertTrue(auth.account.value is Account.SignedIn)
    }

    @Test
    fun `an offline refresh marks shared groups offline`() = runTest {
        val auth = FirebaseAuthRepository(FirebaseAuthApi(offline, "key"), store, available = true)
        val dao = FakeSharedDao()
        dao.groups["g1"] = SharedGroupEntity("g1", "Flat", GroupType.Other.name, "group", true, 1, meMemberId = null, remote = true)
        val sync = SharedSync(auth, FakeFirestore(), FakeSharedRepository(dao), dao)

        sync.syncAll()

        assertEquals(SyncHealth.Offline, sync.status("g1").first().health)
    }

    @Test
    fun `a refresh that lands during sign-out does not sign the account back in`() = runTest {
        val api = GatedApi()
        val auth = FirebaseAuthRepository(api, store, available = true)

        val refresh = async { auth.idToken() }
        api.asked.await()
        val signOut = launch { auth.signOut() }
        runCurrent()
        api.answer.complete(Unit)
        refresh.await()
        signOut.join()

        assertEquals(Account.SignedOut, auth.account.value)
        assertNull(store.value)
    }

    @Test
    fun `firebase asking for a recent sign-in is read where the answer is`() {
        val failure = authFailure(400, """{"error":{"message":"CREDENTIAL_TOO_OLD_LOGIN_AGAIN"}}""")

        assertTrue(failure.needsFreshSignIn)
        assertFalse(failure.sessionEnded)
    }

    @Test
    fun `a dead refresh token ends the session`() {
        val failure = authFailure(400, """{"error":{"message":"TOKEN_EXPIRED"}}""")

        assertTrue(failure.sessionEnded)
        assertFalse(failure.needsFreshSignIn)
    }

    private class MemoryStore(var value: String?) : TokenStore {
        override fun load(): String? = value

        override fun save(value: String) {
            this.value = value
        }

        override fun clear() {
            value = null
        }
    }

    /** A refresh that waits for the test to let it answer. */
    private class GatedApi : AuthApi {
        val asked = CompletableDeferred<Unit>()
        val answer = CompletableDeferred<Unit>()

        override suspend fun refresh(refreshToken: String, nowMillis: Long): AuthTokens {
            asked.complete(Unit)
            answer.await()
            return AuthTokens("uid-1", "id", "r2", nowMillis + HOUR)
        }

        override suspend fun signInWithIdp(providerId: String, idToken: String, rawNonce: String?, nowMillis: Long): AuthTokens = error("unused")

        override suspend fun delete(idToken: String) = error("unused")
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
