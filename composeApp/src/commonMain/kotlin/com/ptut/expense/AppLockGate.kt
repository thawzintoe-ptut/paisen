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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.Spacing
import com.ptut.expense.domain.settings.AppSettings
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.platform.lock.AppLock
import com.ptut.expense.platform.lock.DeviceAuth
import com.ptut.expense.ui.sky.SkyEmptyState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * #25: the lock screen in place of the ledger while [AppLock] says so.
 *
 * The ledger is not composed at all while locked. Sheets and dialogs draw in windows of their own,
 * above anything in this one, so an overlay would leave an open Add sheet readable on top of it.
 * Its saved state is kept in a holder instead, so unlocking puts back the screens and fields left open.
 *
 * The prompt opens by itself each time the lock comes down. A phone whose screen lock was removed
 * since the switch went on can never pass the check, so the lock lifts rather than shutting the
 * person out of their own ledger.
 */
@Composable
internal fun AppLockGate(content: @Composable () -> Unit) {
    val lock = koinInject<AppLock>()
    val auth = koinInject<DeviceAuth>()
    val locked by lock.locked.collectAsStateWithLifecycle()
    val choices by koinInject<AppSettings>().choices.collectAsStateWithLifecycle()
    val text = LocalCopy.current.settings
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { lock.movedToBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { lock.movedToForeground() }
    HideFromAppSwitcher(hidden = choices.appLock)

    suspend fun tryUnlock() {
        if (!auth.isAvailable() || auth.authenticate(text.unlockReason)) lock.unlock()
    }

    LaunchedEffect(locked) { if (locked) tryUnlock() }

    val saved = rememberSaveableStateHolder()
    if (!locked) {
        saved.SaveableStateProvider(LEDGER_KEY) { content() }
        return
    }
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

private const val LEDGER_KEY = "ledger"

/**
 * #25: no picture of the ledger in the app switcher while App lock is on. Android 13 and later
 * leave the preview out and still allow screenshots. iOS draws its cover in `ContentView.swift`.
 */
@Composable
internal expect fun HideFromAppSwitcher(hidden: Boolean)
