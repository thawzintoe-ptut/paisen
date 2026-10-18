package com.ptut.expense.ui

import androidx.compose.runtime.Composable
import com.ptut.expense.data.local.PtutDatabaseContext

/**
 * #216: whether TalkBack or VoiceOver is on. Asked each time an Undo shows rather than once, because
 * either can be switched on while the app runs.
 */
fun interface ScreenReader {
    fun isOn(): Boolean
}

/** For the toast clock in [com.ptut.expense.AppViewModel], which has no composition to read from. */
expect fun screenReader(context: PtutDatabaseContext): ScreenReader

/** The same answer inside a composition, so the Undo bar drains over the window the clock holds. */
@Composable
expect fun rememberScreenReader(): ScreenReader
