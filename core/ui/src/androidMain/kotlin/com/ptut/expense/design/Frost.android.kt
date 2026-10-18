package com.ptut.expense.design

import android.os.Build
import androidx.compose.runtime.Composable

@Composable
actual fun systemAllowsFrost(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
