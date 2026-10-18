package com.ptut.expense.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.Spacing
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyIntro
import com.ptut.expense.ui.sky.SkyListScreen
import com.ptut.expense.ui.sky.SkyNoticeCard

/**
 * FR-22 out: the whole ledger as one file the reader keeps.
 *
 * It says what is going into the file before it opens the picker. An export is the one action in
 * the app that produces something the reader has to look after themselves, and a count is what
 * tells them whether the file they end up with is the one they wanted.
 */
@Composable
fun ExportDataView(
    uiState: LedgerBackupUiState,
    onBack: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.settings
    BackupScreen(
        title = text.exportLedger,
        intro = text.exportIntro,
        cardTitle = uiState.holding,
        // No file name over a ledger with nothing in it: there is no file until there is something to write.
        cardText = if (uiState.exportable) text.savedAs(uiState.fileName) else text.exportEmpty,
        cardIcon = PtutIcon.Download,
        action = text.saveACopy,
        onAction = onExport,
        // An empty file is worse than no file: it imports back as nothing and looks like a bug.
        enabled = uiState.exportable && !uiState.busy,
        footnote = text.exportFootnote,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * FR-22 in: a file the reader saved, read back into the ledger.
 *
 * The merge promise is on the screen rather than in a toast afterwards, because it is the answer
 * to the question that stops people tapping — whether this replaces what they already have.
 * Once a file is chosen the card says what it holds, and nothing merges until Import (#220).
 */
@Composable
fun ImportDataView(
    uiState: LedgerBackupUiState,
    onBack: () -> Unit,
    onChooseFile: () -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = LocalCopy.current
    val text = copy.settings
    val preview = uiState.preview
    BackupScreen(
        title = text.importLedger,
        intro = text.importIntro,
        cardTitle = if (preview == null) text.nothingDeleted else text.inThisFile,
        cardText = preview ?: text.importCardText,
        cardIcon = if (preview == null) PtutIcon.VerifiedUser else PtutIcon.ListAlt,
        action = if (preview == null) text.chooseAFile else text.importLedger,
        onAction = if (preview == null) onChooseFile else onImport,
        // Offered whatever the ledger holds. Restoring into an empty one is the whole point.
        enabled = !uiState.busy,
        footnote = text.importFootnote,
        onBack = onBack,
        modifier = modifier,
        secondary = if (preview == null) null else copy.common.cancel,
        onSecondary = onCancel,
    )
}

/**
 * The shape both ends of FR-22 share: a paragraph, one card stating what will happen, one button.
 *
 * They are one scaffold and one file because they are one seam read twice. Two copies would be
 * two places for the button height and the busy guard to drift apart.
 */
@Composable
private fun BackupScreen(
    title: String,
    intro: String,
    cardTitle: String,
    cardText: String,
    cardIcon: PtutIcon,
    action: String,
    onAction: () -> Unit,
    enabled: Boolean,
    footnote: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    SkyListScreen(title = title, onBack = onBack, modifier = modifier) {
        item { SkyIntro(intro) }
        item {
            SkyNoticeCard(
                title = cardTitle,
                text = cardText,
                icon = cardIcon.image,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        }
        item {
            SkyButton(
                label = action,
                onClick = onAction,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
            )
        }
        if (secondary != null) {
            item {
                SkyButton(
                    label = secondary,
                    onClick = onSecondary,
                    kind = SkyButtonKind.Outline,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
                )
            }
        }
        item {
            Text(
                text = footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }
    }
}
