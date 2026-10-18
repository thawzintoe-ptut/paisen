package com.ptut.expense.design

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/** iOS has the switch outright: Settings › Accessibility › Motion › Reduce Motion. */
@Composable
actual fun systemReduceMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()
