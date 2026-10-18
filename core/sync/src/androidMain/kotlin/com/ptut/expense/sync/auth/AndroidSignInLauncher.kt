package com.ptut.expense.sync.auth

import android.app.Activity
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.ptut.expense.sync.googleWebClientId
import java.security.MessageDigest

/**
 * #187 on Android: Credential Manager's Google sheet. The ID token's audience is the web client id
 * from `firebase.properties`, which Firebase accepts for the same project. No Apple on Android.
 */
@Composable
actual fun rememberSignInLauncher(): SignInLauncher {
    val activity = LocalContext.current as Activity
    return remember(activity) { CredentialManagerSignIn(activity) }
}

private class CredentialManagerSignIn(private val activity: Activity) : SignInLauncher {
    override val offersApple: Boolean = false

    override suspend fun google(): ProviderToken {
        // The button flow, not one-tap: it can add an account, so a phone without one still signs in.
        val option = GetSignInWithGoogleOption.Builder(googleWebClientId).build()
        val result = try {
            CredentialManager.create(activity).getCredential(activity, GetCredentialRequest(listOf(option)))
        } catch (cancelled: GetCredentialCancellationException) {
            throw SignInCancelled()
        }
        val credential = result.credential as? CustomCredential
            ?: throw AuthException("Google sent an unexpected credential")
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) throw AuthException("Google sent an unexpected credential")
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        return ProviderToken(providerId = "google.com", idToken = google.idToken, name = google.givenName ?: google.displayName)
    }

    override suspend fun apple(): ProviderToken = throw AuthException("Sign in with Apple is offered on iOS only")
}

internal actual fun sha256Base64Url(text: String): String =
    Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

internal actual fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/** Android offers Google only, so there is no Apple button to draw (#198). */
@Composable
actual fun AppleSignInButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier) = Unit
