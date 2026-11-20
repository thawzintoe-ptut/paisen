package com.ptut.expense

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.shared.RunDueRepeatsUseCase
import com.ptut.expense.shared.SyncThrottle
import com.ptut.expense.sync.SharedSync
import com.ptut.expense.sync.push.PushEvents
import kotlinx.coroutines.launch
import org.koin.compose.getKoin

/** #189: what other members added arrives whenever the app comes back. */
@Composable
internal fun SyncOnResume(sharedSync: SharedSync?) {
    val koin = getKoin()
    val syncScope = rememberCoroutineScope()
    // #182: repeats due today add themselves after the pull, so another phone's copy arrives first.
    val runDueRepeats = remember(koin) { koin.getOrNull<RunDueRepeatsUseCase>() }
    val resumeSyncs = remember { SyncThrottle() }
    LifecycleResumeEffect(sharedSync, runDueRepeats) {
        val running = syncScope.launch {
            sharedSync?.takeIf { resumeSyncs.tryStart() }?.let { sync -> runSuspendCatching { sync.syncAll() } }
            runDueRepeats?.let { run -> runSuspendCatching { run() } }
        }
        // Only while on screen, so a push that finds nobody listening becomes a notification.
        val pushes = syncScope.launch {
            PushEvents.groupIds.collect { sharedSync?.let { sync -> runSuspendCatching { sync.syncAll() } } }
        }
        onPauseOrDispose {
            running.cancel()
            pushes.cancel()
        }
    }
}
