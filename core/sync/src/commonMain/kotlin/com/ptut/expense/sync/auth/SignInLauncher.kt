package com.ptut.expense.sync.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The person closed the sign-in sheet or the browser. Not an error to show. */
class SignInCancelled : Exception("Sign-in was cancelled")

/**
 * The phone's own sign-in, which only it can show (#187): Credential Manager on Android, a browser
 * session and Sign in with Apple on iOS. What comes back is a provider token for [AuthRepository].
 */
interface SignInLauncher {
    /** Apple guideline 4.8: iOS offers Sign in with Apple beside Google. */
    val offersApple: Boolean

    suspend fun google(): ProviderToken

    suspend fun apple(): ProviderToken
}

/** Needs the screen it is shown from, so it is remembered in composition. */
@Composable
expect fun rememberSignInLauncher(): SignInLauncher

/** SHA-256 of [text], base64url without padding: PKCE's S256 challenge and Apple's nonce. */
internal expect fun sha256Base64Url(text: String): String

/** SHA-256 of [text] as lowercase hex, which is how Apple wants the nonce it signs. */
internal expect fun sha256Hex(text: String): String

/**
 * #198: Apple's own Sign in with Apple button, as App Review asks (guideline 4.8 and the HIG).
 * Only iOS offers Apple; on Android this draws nothing.
 */
@Composable
expect fun AppleSignInButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier)
