package com.ptut.expense.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ptut.expense.data.local.LedgerGate
import com.ptut.expense.data.local.PtutDatabase
import com.ptut.expense.design.Spacing
import com.ptut.expense.locale.recoveryCopy
import com.ptut.expense.platform.crash.CrashJournal
import com.ptut.expense.platform.share.rememberShareSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyLargeTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** "Restore from a backup" opens the ledger fresh first; the shell then opens Import from this inbox. */
object RestoreAfterRecovery {
    private val pending = MutableStateFlow<Unit?>(null)
    val pendingRestore: StateFlow<Unit?> = pending.asStateFlow()

    fun offer() {
        pending.value = Unit
    }

    fun consume() {
        pending.value = null
    }
}

/** Shown instead of the shell when Room refused the ledger. Nothing here deletes a file. */
@Composable
fun LedgerRecoveryScreen(gate: LedgerGate<PtutDatabase>) {
    val words = recoveryCopy()
    val share = rememberShareSheet()
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Spacing.md),
    ) {
        SkyLargeTitle(words.title)
        Text(words.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        SkyButton(
            label = words.export,
            onClick = { gate.readKeptCopy()?.let { share.shareFile(it, UNOPENED_FILE, "application/octet-stream") } },
            kind = SkyButtonKind.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
        SkyButton(label = words.startFresh, onClick = gate::startFresh, modifier = Modifier.fillMaxWidth())
        SkyButton(
            label = words.restore,
            onClick = {
                RestoreAfterRecovery.offer()
                gate.startFresh()
            },
            kind = SkyButtonKind.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Asks once about the last crash. Not now and Share both mark it seen. */
@Composable
fun CrashReportPrompt(journal: CrashJournal) {
    val words = recoveryCopy()
    val share = rememberShareSheet()
    var report by remember { mutableStateOf(journal.unseen()) }
    val waiting = report ?: return
    val answer: (Boolean) -> Unit = { send ->
        journal.markSeen()
        report = null
        if (send) share.shareText(waiting)
    }
    AlertDialog(
        onDismissRequest = { answer(false) },
        title = { Text(words.crashTitle) },
        text = { Text(words.crashBody, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = { answer(true) }) { Text(words.share) } },
        dismissButton = { TextButton(onClick = { answer(false) }) { Text(words.notNow) } },
    )
}

private const val UNOPENED_FILE = "paisen-unopened.db"
