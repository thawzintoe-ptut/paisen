package com.ptut.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.Spacing
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.platform.lock.AppLock
import com.ptut.expense.platform.lock.DeviceAuth
import com.ptut.expense.ui.sky.SkyEmptyState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * #25: the ledger under a lock screen while [AppLock] says so. The shell stays composed under it,
 * so unlocking returns to the same screen, but it is hidden from screen readers and Back.
 *
 * The prompt opens by itself each time the lock comes down. A phone whose screen lock was removed
 * since the switch went on can never pass the check, so the lock lifts rather than shutting the
 * person out of their own ledger.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AppLockGate(content: @Composable () -> Unit) {
    val lock = koinInject<AppLock>()
    val auth = koinInject<DeviceAuth>()
    val locked by lock.locked.collectAsStateWithLifecycle()
    val text = LocalCopy.current.settings
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { lock.movedToBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { lock.movedToForeground() }

    suspend fun tryUnlock() {
        if (!auth.isAvailable() || auth.authenticate(text.unlockReason)) lock.unlock()
    }

    LaunchedEffect(locked) { if (locked) tryUnlock() }

    Box(Modifier.fillMaxSize()) {
        Box(if (locked) Modifier.clearAndSetSemantics {} else Modifier) { content() }
        if (locked) {
            BackHandler(enabled = true, onBack = {})
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .safeDrawingPadding()
                    .padding(Spacing.lg),
            ) {
                SkyEmptyState(
                    headline = text.lockedTitle,
                    body = text.lockedText,
                    status = text.appLockValue,
                    statusIcon = PtutIcon.Lock.image,
                    cta = text.unlock,
                    onCta = { scope.launch { tryUnlock() } },
                )
            }
        }
    }
}
