package com.ptut.expense.sync.auth

/**
 * What only Swift can do for sign-in (#187), handed over once at launch by `iOSApp`:
 * the system browser session, Sign in with Apple, and the Keychain. Kotlin keeps every network
 * call; Swift only shows Apple's sheets and stores bytes.
 */
interface IosAuthBridge {
    /** Opens [url] in ASWebAuthenticationSession and calls back with the URL it returned to, or an error. */
    fun openWebAuth(url: String, callbackScheme: String, completion: (String?, String?) -> Unit)

    /** Sign in with Apple for [hashedNonce]; calls back with the identity token, the given name, or an error. */
    fun signInWithApple(hashedNonce: String, completion: (String?, String?, String?) -> Unit)

    fun keychainLoad(key: String): String?

    fun keychainSave(key: String, value: String)

    fun keychainDelete(key: String)
}

/** Set by Swift before the first frame. Null in tests, where nothing signs in. */
object IosBridges {
    var auth: IosAuthBridge? = null
}

/** Called from `iOSApp.init`. */
fun installAuthBridge(bridge: IosAuthBridge) {
    IosBridges.auth = bridge
}

/** The cancellation error Swift reports when the person closes a sheet. */
internal const val CANCELLED = "cancelled"
