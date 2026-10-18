package com.ptut.expense.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberShareText(): (String) -> Unit = remember {
    { text ->
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        top?.presentViewController(sheet, animated = true, completion = null)
    }
}
