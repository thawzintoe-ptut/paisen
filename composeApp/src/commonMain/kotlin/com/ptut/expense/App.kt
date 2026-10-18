package com.ptut.expense

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.LocalCopy

/**
 * Takes nothing: each screen resolves its own state holder from the graph, so the shell has no
 * dependency to thread down. Koin is started by the platform entry point before this runs.
 *
 * Keyed on the language: a change rebuilds the shell, so a screen that wrote its words once,
 * when it was built, writes them again in the new one.
 */
@Composable
fun App() {
    val language by AppText.language.collectAsState()
    key(language) {
        CompositionLocalProvider(LocalCopy provides language.copy) { PtutApp() }
    }
}
