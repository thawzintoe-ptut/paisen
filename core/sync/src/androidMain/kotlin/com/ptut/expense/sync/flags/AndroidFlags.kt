package com.ptut.expense.sync.flags

import android.content.Context
import com.google.firebase.installations.FirebaseInstallations
import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.sync.FirebaseConfig
import com.ptut.expense.sync.push.firebaseAppReady
import kotlinx.coroutines.tasks.await

internal actual fun flagCache(context: PtutDatabaseContext): FlagCache = SharedPreferencesFlagCache(context.applicationContext)

internal actual fun installationSource(context: PtutDatabaseContext): suspend () -> Installation? {
    val app = context.applicationContext
    return {
        if (firebaseAppReady(app)) {
            val installations = FirebaseInstallations.getInstance()
            Installation(
                id = installations.id.await(),
                token = installations.getToken(false).await().token,
                appId = FirebaseConfig.ANDROID_APP_ID,
                packageName = app.packageName,
            )
        } else {
            null
        }
    }
}

private class SharedPreferencesFlagCache(context: Context) : FlagCache {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read(): StoredFlags = decodeFlags(prefs.getString(KEY_ENTRIES, null), prefs.getLong(KEY_FETCHED_AT, 0))

    override fun write(flags: StoredFlags) {
        prefs.edit().putString(KEY_ENTRIES, encodeEntries(flags.entries)).putLong(KEY_FETCHED_AT, flags.fetchedAtMillis).apply()
    }

    private companion object {
        const val FILE = "remote_flags"
        const val KEY_ENTRIES = "entries"
        const val KEY_FETCHED_AT = "fetched_at"
    }
}
