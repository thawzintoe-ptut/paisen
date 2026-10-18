package com.ptut.expense.sync.push

import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.FirestoreClient
import com.ptut.expense.sync.auth.Account
import com.ptut.expense.sync.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Keeps this phone's `devices/{token}` document in step with the account, the FCM token and the
 * app's language, so the Worker knows where to push and in which words.
 */
class PushRegistrar internal constructor(
    private val auth: AuthRepository,
    private val tokens: PushTokenHolder,
    private val firestore: FirestoreClient,
    /** False in a build with no push Worker: nothing registers. */
    private val enabled: Boolean,
    private val clock: Clock = Clock.System,
) {
    /** The token whose document this phone last wrote, so sign-out removes the right one. */
    private var registered: String? = null

    fun start(scope: CoroutineScope) {
        if (!enabled) return
        scope.launch {
            combine(auth.account, tokens.token, AppText.language) { account, token, language -> Triple(account, token, language) }
                .distinctUntilChanged()
                .collectLatest { (account, token, language) ->
                    if (account !is Account.SignedIn || token == null) return@collectLatest
                    // A failure waits for the next change or launch; the phone still pulls on its own.
                    runCatching {
                        firestore.set(
                            "devices/$token",
                            mapOf(
                                "uid" to account.uid,
                                "platform" to pushPlatform,
                                "language" to language.tag,
                                "updatedAt" to clock.now().toEpochMilliseconds(),
                            ),
                        )
                        registered = token
                    }
                }
        }
    }

    /** Called while the account is still signed in, because the rules let only the owner delete. */
    suspend fun forgetDevice() {
        if (!enabled) return
        val token = registered ?: tokens.token.value ?: return
        runCatching { firestore.delete("devices/$token") }
        registered = null
    }
}
