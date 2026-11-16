package com.ptut.expense

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/** Android 12 and earlier could only use FLAG_SECURE, which also blocks screenshots, so they keep the preview. */
@Composable
internal actual fun HideFromAppSwitcher(hidden: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity, hidden) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) activity.setRecentsScreenshotEnabled(!hidden)
        onDispose { }
    }
}
