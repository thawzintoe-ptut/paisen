package com.ptut.expense.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyGroupedCard
import com.ptut.expense.ui.sky.SkyIntro
import com.ptut.expense.ui.sky.SkyListScreen
import com.ptut.expense.ui.sky.SkyNoticeCard
import com.ptut.expense.ui.sky.SkySwitch

/**
 * FR-2 and FR-3: whether the app reads your folders while it is closed, and when it last did.
 *
 * The switch is drawn only where there is a schedule behind it. C-4 leaves iOS with none, and the
 * screen says so in the platform's own words instead of drawing a control that would lie — but it
 * still shows the last run and still offers the scan, because those are the two things a reader
 * who came here wondering why nothing arrived actually needs.
 */
@Composable
fun BackgroundImportView(
    uiState: BackgroundImportUiState,
    onBack: () -> Unit,
    onSetRunning: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.settings
    SkyListScreen(title = text.backgroundImport, onBack = onBack, modifier = modifier) {
        item { SkyIntro(uiState.explanation) }
        if (uiState.adjustable) {
            item {
                // The one control here, so a row of its own rather than a line in a card.
                SkyGroupedCard(
                    rows = listOf {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = SkySizes.transactionRow)
                                .tappable(PtutRadii.r12, Role.Switch) { onSetRunning(!uiState.running) }
                                .padding(horizontal = Spacing.sm, vertical = Spacing.s12),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text.scanInBackground, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                // The rhythm belongs to the switch: what is turned on is the cadence.
                                Text(
                                    text = if (uiState.running) uiState.rhythm else text.off,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            SkySwitch(checked = uiState.running, onCheckedChange = null)
                        }
                    },
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )
            }
        } else {
            item {
                SkyNoticeCard(
                    title = uiState.rhythm,
                    text = text.noSchedule,
                    icon = PtutIcon.Smartphone.image,
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )
            }
        }
        item {
            // The freshness line, in the shape Photo folders uses, so the two read as one claim.
            Row(
                modifier = Modifier.padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PtutIcon.History.image, contentDescription = null, tint = MaterialTheme.ptut.ok, modifier = Modifier.size(Spacing.sm))
                Text(uiState.lastRun, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SkyButton(
                label = if (uiState.checking) text.looking else text.checkNow,
                onClick = onCheckNow,
                kind = SkyButtonKind.Outline,
                enabled = !uiState.checking,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
            )
        }
        item {
            Text(
                text = text.turningOffNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }
    }
}
