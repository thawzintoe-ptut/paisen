package com.ptut.expense.platform.lock

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * The platform's own BiometricPrompt, not androidx.biometric: no new library, and no move to a
 * FragmentActivity. It needs Android 9, so Android 8 reports the lock as unavailable.
 *
 * The prompt draws over an Activity, so this keeps a weak hold on the resumed one rather than
 * asking MainActivity to register itself.
 */
class BiometricDeviceAuth(context: Context) : DeviceAuth {

    private val app = context.applicationContext as Application
    private val keyguard = app.getSystemService(KeyguardManager::class.java)
    private var resumed: WeakReference<Activity>? = null

    init {
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    resumed = WeakReference(activity)
                }

                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                // Started as well as resumed: a cold start asks before the first resume.
                override fun onActivityStarted(activity: Activity) {
                    resumed = WeakReference(activity)
                }

                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) {
                    if (resumed?.get() === activity) resumed = null
                }
            },
        )
    }

    override fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && keyguard?.isDeviceSecure == true

    override suspend fun authenticate(reason: String): Boolean {
        if (!isAvailable()) return false
        val activity = resumed?.get() ?: return false
        return withContext(Dispatchers.Main) { prompt(activity, reason) }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private suspend fun prompt(activity: Activity, reason: String): Boolean =
        suspendCancellableCoroutine { result ->
            val signal = CancellationSignal()
            result.invokeOnCancellation { signal.cancel() }
            builder(activity, reason).build().authenticate(
                signal,
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(outcome: BiometricPrompt.AuthenticationResult) {
                        if (result.isActive) result.resume(true)
                    }

                    // A wrong finger keeps the prompt open; only an error or a cancel ends it.
                    override fun onAuthenticationError(code: Int, message: CharSequence) {
                        if (result.isActive) result.resume(false)
                    }
                },
            )
        }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun builder(activity: Activity, reason: String): BiometricPrompt.Builder {
        val builder = BiometricPrompt.Builder(activity).setTitle(reason)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
    }
}
