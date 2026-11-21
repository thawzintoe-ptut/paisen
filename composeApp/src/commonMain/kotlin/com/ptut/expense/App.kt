package com.ptut.expense

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.ptut.expense.data.local.LedgerGate
import com.ptut.expense.data.local.LedgerOpen
import com.ptut.expense.data.local.PtutDatabase
import com.ptut.expense.design.PtutTheme
import com.ptut.expense.domain.Home
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.platform.crash.CrashJournal
import com.ptut.expense.recovery.CrashReportPrompt
import com.ptut.expense.recovery.LedgerRecoveryScreen
import org.koin.compose.koinInject

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
    // #41: a new home rewrites every amount and every day, so it rebuilds the shell as a language does.
    val home by Home.choice.collectAsState()
    val gate = koinInject<LedgerGate<PtutDatabase>>()
    val ledger by gate.state.collectAsState()
    key(language, home) {
        CompositionLocalProvider(LocalCopy provides language.copy) {
            if (ledger is LedgerOpen.Ready) {
                PtutApp()
                PtutTheme { CrashReportPrompt(koinInject<CrashJournal>()) }
            } else {
                // The ledger file can be exported from here, so it sits behind the same lock as the ledger.
                PtutTheme { AppLockGate { LedgerRecoveryScreen(gate) } }
            }
        }
    }
}
