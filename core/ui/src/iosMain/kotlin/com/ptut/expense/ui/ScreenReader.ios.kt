package com.ptut.expense.ui

import androidx.compose.runtime.Composable
import com.ptut.expense.data.local.PtutDatabaseContext
import platform.UIKit.UIAccessibilityIsVoiceOverRunning

private val VoiceOver = ScreenReader { UIAccessibilityIsVoiceOverRunning() }

actual fun screenReader(context: PtutDatabaseContext): ScreenReader = VoiceOver

@Composable
actual fun rememberScreenReader(): ScreenReader = VoiceOver
