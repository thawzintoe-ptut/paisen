package com.ptut.expense.platform.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.shared.RunDueRepeatsUseCase
import com.ptut.expense.sync.SharedSync
import com.ptut.expense.sync.SyncHealth
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

private const val PERIODIC_SHARED_SYNC = "ptut-shared-sync-periodic"

/** Each shared-group pull costs Firestore reads from a free daily allowance every member shares. */
private const val SHARED_SYNC_INTERVAL_HOURS = 6L

/**
 * Shared groups, pulled while the app is closed. Its own six-hourly job with a network constraint, so
 * the fifteen-minute slip scan neither spends Firestore reads nor wakes the radio. It runs whether
 * or not Background import is on, and only while an account is signed in.
 */
class SharedSyncWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters), KoinComponent {

    private val runDueRepeats: RunDueRepeatsUseCase by inject()

    /** Null in a graph without sync, as on the app's own start. */
    private val sync: SharedSync? by lazy { getKoin().getOrNull<SharedSync>() }

    /** A failed pull is reported, not retried, as [SlipImportWorker] does: the next hour is the retry. */
    override suspend fun doWork(): Result {
        val sync = sync?.takeIf { it.available && it.isSignedIn }
        if (sync == null) {
            applicationContext.stopSharedSync()
            return Result.success()
        }
        val pulled = runSuspendCatching { sync.syncAll() }.isSuccess && sync.overallStatus().first().health != SyncHealth.Offline
        // After the pull: a repeat entry another phone already added arrives before this phone makes its own.
        runSuspendCatching { runDueRepeats() }
        return if (pulled) Result.success() else Result.failure()
    }
}

/** The hourly shared sync while an account is signed in, and none once it is not. */
fun Context.matchSharedSyncTo(signedIn: Boolean) {
    if (signedIn) syncSharedGroupsHourly() else stopSharedSync()
}

/** `UPDATE` rather than `KEEP`, as the slip scan does, so a changed interval takes effect on the next launch. */
fun Context.syncSharedGroupsHourly() {
    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        PERIODIC_SHARED_SYNC,
        ExistingPeriodicWorkPolicy.UPDATE,
        PeriodicWorkRequestBuilder<SharedSyncWorker>(SHARED_SYNC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build(),
    )
}

fun Context.stopSharedSync() {
    WorkManager.getInstance(this).cancelUniqueWork(PERIODIC_SHARED_SYNC)
}
