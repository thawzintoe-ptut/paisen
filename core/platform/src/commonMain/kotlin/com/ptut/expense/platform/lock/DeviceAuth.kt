package com.ptut.expense.platform.lock

/**
 * The phone's own check: fingerprint, face, or its PIN, pattern or password (#25). BiometricPrompt
 * on Android, LocalAuthentication on iOS. The app never sees or stores any of it.
 */
interface DeviceAuth {

    /** The phone has a screen lock set and the OS can ask for it. False on Android 8. */
    fun isAvailable(): Boolean

    /** True only when the person passed. A cancel, a lockout or an error is false. */
    suspend fun authenticate(reason: String): Boolean
}
