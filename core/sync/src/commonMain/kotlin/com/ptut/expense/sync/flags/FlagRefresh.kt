package com.ptut.expense.sync.flags

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Where the fetched entries survive between launches: `SharedPreferences` on Android, `NSUserDefaults` on iOS. */
interface FlagCache {
    fun read(): StoredFlags

    fun write(flags: StoredFlags)
}

internal expect fun flagCache(context: PtutDatabaseContext): FlagCache

/** This phone's Firebase installation, or null where the platform has no Firebase app. */
internal expect fun installationSource(context: PtutDatabaseContext): suspend () -> Installation?

/**
 * Fetches Remote Config at most every [FETCH_INTERVAL], off the main thread, after the first screen.
 * Every failure is silent and keeps the cache; the new values apply at the next launch.
 */
class FlagRefresh internal constructor(
    /** Null in a build without a Firebase project, which never calls out. */
    private val fetch: (suspend (Installation, String, String) -> FetchReply?)?,
    private val cache: FlagCache,
    private val installation: suspend () -> Installation?,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    private var started = false

    fun start(appVersion: String, languageCode: String) {
        if (started || fetch == null) return
        started = true
        scope.launch { runSuspendCatching { refresh(appVersion, languageCode) } }
    }

    internal suspend fun refresh(appVersion: String, languageCode: String) {
        val stored = cache.read()
        val now = clock.now().toEpochMilliseconds()
        if (fetch == null || !fetchDue(stored, now)) return
        val asking = installation() ?: return
        val reply = fetch(asking, appVersion, languageCode) ?: return
        stored.after(reply, now)?.let(cache::write)
    }
}
