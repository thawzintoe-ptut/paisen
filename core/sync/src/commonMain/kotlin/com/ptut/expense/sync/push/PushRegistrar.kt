package com.ptut.expense.sync.push

import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.Firestore
import com.ptut.expense.sync.FirestoreException
import com.ptut.expense.sync.FirestoreStatus
import com.ptut.expense.sync.auth.Account
import com.ptut.expense.sync.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps this phone's `devices/{token}` document in step with the account, the FCM token and the
 * app's language, so the Worker knows where to push and in which words.
 */
class PushRegistrar internal constructor(
    private val auth: AuthRepository,
    private val tokens: PushTokenHolder,
    private val firestore: Firestore,
    /** False in a build with no push Worker: nothing registers. */
    private val enabled: Boolean,
    private val clock: Clock = Clock.System,
    private val leftovers: PushLeftovers = pushLeftovers(),
) {
    /** The token whose document this phone last wrote, so sign-out removes the right one. Set and read from several threads. */
    private val registered = MutableStateFlow<String?>(null)

    fun start(scope: CoroutineScope) {
        if (!enabled) return
        scope.launch {
            combine(auth.account, tokens.token, AppText.language) { account, token, language -> Triple(account, token, language) }
                .distinctUntilChanged()
                .collectLatest { (account, token, language) ->
                    settleLeftover(account, token)
                    if (account !is Account.SignedIn || token == null) return@collectLatest
                    // A failure that may pass (no network at launch, a token not ready) tries again after a
                    // growing wait, or this phone hears no push until the next launch. A refusal does not.
                    for (wait in listOf(Duration.ZERO) + RETRY_WAITS) {
                        delay(wait)
                        val written = runSuspendCatching {
                            firestore.set(
                                "devices/$token",
                                mapOf(
                                    "uid" to account.uid,
                                    "platform" to pushPlatform,
                                    "language" to language.tag,
                                    "updatedAt" to clock.now().toEpochMilliseconds(),
                                ),
                            )
                            registered.value = token
                        }
                        val refused = (written.exceptionOrNull() as? FirestoreException)?.let { FirestoreStatus.isPermanent(it.status) } == true
                        if (written.isSuccess || refused) break
                    }
                }
        }
    }

    /**
     * Called while the account is still signed in, because the rules let only the owner delete. A
     * delete that fails, offline say, is kept as a [PushLeftover] for a later start to settle.
     */
    suspend fun forgetDevice() {
        if (!enabled) return
        val token = registered.value ?: tokens.token.value ?: return
        val uid = (auth.account.value as? Account.SignedIn)?.uid
        val deleted = runSuspendCatching { firestore.delete("devices/$token") }.isSuccess
        if (!deleted && uid != null) leftovers.write(PushLeftover(token, uid))
        // Only the token deleted here: one registered meanwhile still needs removing.
        registered.compareAndSet(token, null)
    }

    /**
     * One phone per account: the account's documents for other phones go, so the phone this sign-in
     * replaced stops getting its pushes. Best effort; kept while this phone's own ID is not known yet.
     */
    suspend fun forgetOtherDevices() {
        if (!enabled) return
        val uid = (auth.account.value as? Account.SignedIn)?.uid ?: return
        val mine = registered.value ?: tokens.token.value ?: return
        runSuspendCatching {
            firestore.whereEquals("devices", "uid", uid).filter { it.id != mine }.forEach { firestore.delete("devices/${it.id}") }
        }
    }

    /**
     * L3: the same account deletes its old document, or registers this one over it. Signed out or
     * as another account, the rules allow no delete, so the platform retires the installation ID.
     */
    private suspend fun settleLeftover(account: Account, token: String?) {
        val leftover = leftovers.read() ?: return
        val owner = (account as? Account.SignedIn)?.uid == leftover.uid
        val settled = when {
            owner && leftover.token == token -> true
            owner -> deleted(leftover.token)
            else -> leftovers.retire(leftover.token)
        }
        if (settled) leftovers.write(null)
    }

    /** Gone, or refused for good: a document that is not there or not this account's needs nothing more. */
    private suspend fun deleted(token: String): Boolean {
        val result = runSuspendCatching { firestore.delete("devices/$token") }
        val refusal = result.exceptionOrNull() as? FirestoreException
        return result.isSuccess || (refusal != null && FirestoreStatus.isPermanent(refusal.status))
    }
}

private val RETRY_WAITS = listOf(5.seconds, 30.seconds, 2.minutes, 10.minutes)
