package com.ptut.expense.platform.lock

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import kotlin.coroutines.resume

/**
 * Face ID or Touch ID, with the passcode as the fallback (`deviceOwnerAuthentication`). Info.plist
 * carries `NSFaceIDUsageDescription`; without it iOS ends the app on the first Face ID prompt.
 */
@OptIn(ExperimentalForeignApi::class)
class LocalAuthDeviceAuth : DeviceAuth {

    override fun isAvailable(): Boolean =
        LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)

    override suspend fun authenticate(reason: String): Boolean = suspendCancellableCoroutine { result ->
        val context = LAContext()
        result.invokeOnCancellation { context.invalidate() }
        context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = reason) { passed, _ ->
            if (result.isActive) result.resume(passed)
        }
    }
}
