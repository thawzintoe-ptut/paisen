package com.ptut.expense.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.tappable
import com.ptut.expense.platform.notify.rememberNotificationAsk
import com.ptut.expense.ui.sky.SkyGroupTitle
import com.ptut.expense.ui.sky.SkyGroupedCard
import com.ptut.expense.ui.sky.SkyListScreen
import com.ptut.expense.ui.sky.SkySwitch
import kotlinx.coroutines.launch

/** #83, Settings › Play (p22): the master switch, then its three groups while it is on. */
@Composable
fun PlaySettingsView(
    uiState: PlaySettingsUiState,
    onSet: (PlaySetting, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ask = rememberNotificationAsk()
    val scope = rememberCoroutineScope()
    // D15: turning the reminder on asks for notifications first, as a repeat's first save does.
    val set: (PlaySetting, Boolean) -> Unit = { setting, on ->
        if (setting == PlaySetting.WeeklyReminder && on) scope.launch { ask.request().also { onSet(setting, on) } } else onSet(setting, on)
    }
    SkyListScreen(title = uiState.title, onBack = onBack, modifier = modifier) {
        item {
            SkyGroupedCard(
                rows = listOf<@Composable () -> Unit>({ PlaySwitchRow(uiState.master, set) }),
                modifier = Modifier.padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.xs),
            )
        }
        uiState.groups.forEach { group ->
            item(key = group.title) { SkyGroupTitle(group.title, Modifier.padding(top = Spacing.s20)) }
            item(key = "${group.title}-rows") {
                SkyGroupedCard(
                    rows = group.rows.map<PlaySettingRowUiModel, @Composable () -> Unit> { row -> { PlaySwitchRow(row, set) } },
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )
            }
        }
        uiState.footer?.let { footer ->
            item {
                Text(
                    text = footer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.s28, end = Spacing.s28, top = Spacing.s12),
                )
            }
        }
    }
}

/** The whole row is the switch, as Settings' own switch rows are. */
@Composable
private fun PlaySwitchRow(row: PlaySettingRowUiModel, onSet: (PlaySetting, Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.transactionRow)
            .tappable(PtutRadii.r12, Role.Switch) { onSet(row.id, !row.on) }
            .padding(horizontal = Spacing.sm, vertical = Spacing.s12),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(row.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SkySwitch(checked = row.on, onCheckedChange = null)
    }
}
