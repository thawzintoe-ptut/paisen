package com.ptut.expense.sync.flags

import com.ptut.expense.data.local.PtutDatabaseContext
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults
import kotlin.coroutines.resume

/**
 * Firebase Installations lives in the Swift target, like Messaging, so the Kotlin framework gains no
 * SDK. Swift calls back with the installation ID, its auth token and the plist's Firebase app id.
 */
interface IosInstallationBridge {
    fun installation(completion: (String?, String?, String?) -> Unit)
}

private object InstallationBridge {
    var bridge: IosInstallationBridge? = null
}

/** Called from the app delegate once Firebase is configured. */
fun installInstallationBridge(bridge: IosInstallationBridge) {
    InstallationBridge.bridge = bridge
}

internal actual fun flagCache(context: PtutDatabaseContext): FlagCache = UserDefaultsFlagCache()

internal actual fun installationSource(context: PtutDatabaseContext): suspend () -> Installation? = {
    InstallationBridge.bridge?.let { bridge ->
        suspendCancellableCoroutine { continuation ->
            bridge.installation { id, token, appId ->
                val installation = if (id != null && token != null && appId != null) {
                    Installation(id, token, appId, NSBundle.mainBundle.bundleIdentifier.orEmpty())
                } else {
                    null
                }
                if (continuation.isActive) continuation.resume(installation)
            }
        }
    }
}

private class UserDefaultsFlagCache : FlagCache {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(): StoredFlags =
        decodeFlags(defaults.stringForKey(KEY_ENTRIES), defaults.stringForKey(KEY_FETCHED_AT)?.toLongOrNull() ?: 0)

    override fun write(flags: StoredFlags) {
        defaults.setObject(encodeEntries(flags.entries), forKey = KEY_ENTRIES)
        defaults.setObject(flags.fetchedAtMillis.toString(), forKey = KEY_FETCHED_AT)
    }

    private companion object {
        const val KEY_ENTRIES = "ptut-remote-flags"
        const val KEY_FETCHED_AT = "ptut-remote-flags-fetched-at"
    }
}
