package com.ptut.expense.sync.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/** Who this phone is signed in as, for shared groups only (#187). Your own spending needs no account. */
sealed interface Account {
    data object SignedOut : Account

    data class SignedIn(val uid: String, val name: String, val email: String?, val provider: String) : Account
}

/** A Google or Apple ID token from the phone's own sign-in, before Firebase turns it into an account. */
data class ProviderToken(val providerId: String, val idToken: String, val rawNonce: String? = null, val name: String? = null)

/**
 * #187. Sign-in is asked for only when someone shares a group or opens an invite. [available] is
 * false until `firebase.properties` names a project, and then nothing asks at all.
 */
interface AuthRepository {
    val available: Boolean
    val account: StateFlow<Account>

    suspend fun signIn(token: ProviderToken): Account.SignedIn

    /** A fresh Firebase ID token, refreshed when the stored one is about to expire. */
    suspend fun idToken(): String

    suspend fun signOut()

    /** Apple guideline 5.1.1(v). The caller lets go of shared groups first; this removes the account. */
    suspend fun deleteAccount()
}

/** Where the refresh token lives: the Android Keystore or the iOS Keychain, never plain storage. */
interface TokenStore {
    fun load(): String?

    fun save(value: String)

    fun clear()
}

@Serializable
private data class StoredSession(
    val uid: String,
    val refreshToken: String,
    val name: String,
    val email: String? = null,
    val provider: String,
)

internal class FirebaseAuthRepository(
    private val api: FirebaseAuthApi,
    private val store: TokenStore,
    override val available: Boolean,
    private val clock: Clock = Clock.System,
) : AuthRepository {

    private val session = MutableStateFlow(store.load()?.let { runCatching { Json.decodeFromString<StoredSession>(it) }.getOrNull() })
    private var cached: AuthTokens? = null
    private val refreshing = Mutex()

    private val accountFlow = MutableStateFlow(session.value.toAccount())
    override val account: StateFlow<Account> = accountFlow

    override suspend fun signIn(token: ProviderToken): Account.SignedIn {
        val now = clock.now().toEpochMilliseconds()
        val tokens = api.signInWithIdp(token.providerId, token.idToken, token.rawNonce, now)
        val stored = StoredSession(
            uid = tokens.uid,
            refreshToken = tokens.refreshToken,
            name = token.name ?: tokens.displayName ?: tokens.email?.substringBefore('@') ?: "Me",
            email = tokens.email,
            provider = token.providerId,
        )
        remember(stored, tokens)
        return stored.toAccount() as Account.SignedIn
    }

    override suspend fun idToken(): String = refreshing.withLock {
        val now = clock.now().toEpochMilliseconds()
        cached?.takeIf { it.expiresAtEpochMillis - EARLY_REFRESH_MILLIS > now }?.let { return@withLock it.idToken }
        val stored = session.value ?: throw AuthException("Not signed in", sessionEnded = true)
        val tokens = try {
            api.refresh(stored.refreshToken, now)
        } catch (failure: AuthException) {
            // #195: a dead session signs out, so every screen offers Sign in. Shared groups stay
            // on the phone and sync again once the same account is back.
            if (failure.sessionEnded) clearSession()
            throw failure
        }
        remember(stored.copy(refreshToken = tokens.refreshToken), tokens)
        tokens.idToken
    }

    override suspend fun signOut() = clearSession()

    private fun clearSession() {
        cached = null
        session.value = null
        store.clear()
        accountFlow.value = Account.SignedOut
    }

    override suspend fun deleteAccount() {
        api.delete(idToken())
        signOut()
    }

    private fun remember(stored: StoredSession, tokens: AuthTokens) {
        cached = tokens
        session.value = stored
        store.save(Json.encodeToString(StoredSession.serializer(), stored))
        accountFlow.value = stored.toAccount()
    }

    private fun StoredSession?.toAccount(): Account =
        if (this == null) Account.SignedOut else Account.SignedIn(uid, name, email, provider)

    private companion object {
        /** Refresh a minute early, so a token never expires mid-request. */
        const val EARLY_REFRESH_MILLIS = 60_000L
    }
}

/** Nothing is ever stored: for builds without a platform store, and for tests. */
class NoTokenStore : TokenStore {
    override fun load(): String? = null

    override fun save(value: String) = Unit

    override fun clear() = Unit
}
