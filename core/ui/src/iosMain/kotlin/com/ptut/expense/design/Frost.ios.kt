package com.ptut.expense.design

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceTransparencyEnabled

@Composable
actual fun systemAllowsFrost(): Boolean = !UIAccessibilityIsReduceTransparencyEnabled()
