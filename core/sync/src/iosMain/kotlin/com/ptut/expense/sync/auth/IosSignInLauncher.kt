package com.ptut.expense.sync.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.compose.koinInject
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * #187 on iOS: Google through the system browser (no Google SDK in the app) and Sign in with Apple,
 * which App Store rule 4.8 asks for beside Google.
 */
@Composable
actual fun rememberSignInLauncher(): SignInLauncher {
    val google = koinInject<GoogleOAuth>()
    return remember(google) { BridgeSignIn(google) }
}

private class BridgeSignIn(private val oauth: GoogleOAuth) : SignInLauncher {
    override val offersApple: Boolean = true

    override suspend fun google(): ProviderToken {
        val bridge = IosBridges.auth ?: throw AuthException("Sign-in is not set up in this build")
        val request = oauth.request()
        val callback = suspendCancellableCoroutine { continuation ->
            bridge.openWebAuth(request.url, oauth.callbackScheme) { url, error ->
                when {
                    url != null -> continuation.resume(url)
                    error == CANCELLED -> continuation.resumeWithException(SignInCancelled())
                    else -> continuation.resumeWithException(AuthException(error ?: "Google sign-in stopped"))
                }
            }
        }
        return ProviderToken(providerId = "google.com", idToken = oauth.exchange(callback, request))
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun apple(): ProviderToken {
        val bridge = IosBridges.auth ?: throw AuthException("Sign-in is not set up in this build")
        val rawNonce = Uuid.random().toHexString()
        return suspendCancellableCoroutine { continuation ->
            bridge.signInWithApple(sha256Hex(rawNonce)) { token, name, error ->
                when {
                    token != null -> continuation.resume(ProviderToken("apple.com", token, rawNonce = rawNonce, name = name))
                    error == CANCELLED -> continuation.resumeWithException(SignInCancelled())
                    else -> continuation.resumeWithException(AuthException(error ?: "Sign in with Apple stopped"))
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun sha256(text: String): ByteArray {
    val input = text.encodeToByteArray()
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    input.usePinned { source ->
        digest.usePinned { out ->
            CC_SHA256(source.addressOf(0), input.size.convert(), out.addressOf(0).reinterpret())
        }
    }
    return digest
}

@OptIn(ExperimentalEncodingApi::class)
internal actual fun sha256Base64Url(text: String): String = Base64.UrlSafe.encode(sha256(text)).trimEnd('=')

internal actual fun sha256Hex(text: String): String = sha256(text).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
