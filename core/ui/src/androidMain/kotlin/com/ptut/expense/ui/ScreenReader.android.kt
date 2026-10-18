package com.ptut.expense.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ptut.expense.data.local.PtutDatabaseContext

/** Touch exploration is what TalkBack, and every Android screen reader, switches on. */
actual fun screenReader(context: PtutDatabaseContext): ScreenReader {
    val manager = context.applicationContext.getSystemService(AccessibilityManager::class.java)
    return ScreenReader { manager?.isTouchExplorationEnabled == true }
}

@Composable
actual fun rememberScreenReader(): ScreenReader {
    val context = LocalContext.current
    return remember(context) { screenReader(context) }
}
