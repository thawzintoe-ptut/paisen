package com.ptut.expense.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutIcon
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyGroupTitle
import com.ptut.expense.ui.sky.SkyGroupedCard
import com.ptut.expense.ui.sky.SkyIntro
import com.ptut.expense.ui.sky.SkyLargeTitle
import com.ptut.expense.ui.sky.SkyListScreen
import com.ptut.expense.ui.sky.SkySwitch

/** What a screen reader says about the row the stale-import notice sent the reader to. */
val NEEDS_ATTENTION: String get() = AppText.copy.settings.needsAttention

/**
 * Settings in Sky (p21): the large title, then four white cards: Saving, You, Keep it safe, and
 * Account and help, with the offline promise as the footer. Every row opens a screen. A switch
 * row with a screen behind it, Android's background import (C-4), flips only from its switch, so the
 * row the stale notice points at opens that screen instead of turning the import off (#217).
 *
 * Kept beyond the artboard (#146, K26): the red dot and spoken mark on the row that owns a stale import's
 * fix, and the debug panels in debug builds.
 */
@Composable
fun SettingsView(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    focusRow: SettingsAction? = null,
    /** A switch row's lever, named by its row, so a second switch needs no second callback (#121). */
    onSwitch: (SettingsAction, Boolean) -> Unit = { _, _ -> },
    footer: (@Composable () -> Unit)? = null,
    /** #187: the shared-groups account row, first in the Account and help card; null when the build has none. */
    account: (@Composable () -> Unit)? = null,
) {
    val text = LocalCopy.current.settings
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        item { SkyLargeTitle(text.title, Modifier.padding(top = Spacing.md)) }
        // #101: a fix that sits behind Advanced marks the Advanced row, the first of its two taps.
        val focusBehindAdvanced = focusRow != null && uiState.advanced.any { group -> group.rows.any { it.id == focusRow } }
        settingsCards(uiState.groups, onAction, onSwitch, leading = mapOf(SettingsGroup.Account to account)) { row ->
            row.id == focusRow || (row.id == SettingsAction.Advanced && focusBehindAdvanced)
        }
        // NFR-1 as #191 narrowed it, and NFR-5, said once at the end rather than in a privacy page.
        item { SkyIntro(text.footer, Modifier.padding(top = Spacing.xs)) }
        footer?.let { item { it() } }
        item { Box(Modifier.height(SkySizes.pillClearance)) }
    }
}

/** #101: Settings › Advanced, the rows most people never touch, in the same cards as Settings. */
@Composable
fun SettingsAdvancedView(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    focusRow: SettingsAction? = null,
    onSwitch: (SettingsAction, Boolean) -> Unit = { _, _ -> },
) {
    val text = LocalCopy.current.settings
    SkyListScreen(title = text.advanced, onBack = onBack, modifier = modifier) {
        item { SkyIntro(text.advancedIntro) }
        settingsCards(uiState.advanced, onAction, onSwitch) { row -> row.id == focusRow }
        item { Box(Modifier.height(SkySizes.pillClearance)) }
    }
}

/**
 * Each group's title, then its white card. [sentFor] says the reader was sent here for that row;
 * [leading] is a row the shell draws at the top of a card, such as the account row.
 */
private fun LazyListScope.settingsCards(
    groups: List<SettingsGroupUiModel>,
    onAction: (SettingsAction) -> Unit,
    onSwitch: (SettingsAction, Boolean) -> Unit,
    leading: Map<SettingsGroup, (@Composable () -> Unit)?> = emptyMap(),
    sentFor: (SettingsRowUiModel) -> Boolean,
) {
    groups.forEach { group ->
        group.title?.let { title -> item(key = "${group.group.name}-title") { SkyGroupTitle(title, Modifier.padding(top = Spacing.xs)) } }
        item(key = group.group.name) {
            SkyGroupedCard(
                rows = listOfNotNull(leading[group.group]) + group.rows.map<SettingsRowUiModel, @Composable () -> Unit> { row ->
                    {
                        SettingsRow(
                            row = row,
                            // Marked only while the reason still stands and the reader was sent here for it.
                            marked = row.needsAttention && sentFor(row),
                            onClick = { onAction(row.id) },
                            onSwitch = { on -> onSwitch(row.id, on) },
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        }
    }
}

/** One Settings row: glyph, name and value, then a switch or a chevron. Public for the shell's account row. */
@Composable
fun SettingsRow(
    row: SettingsRowUiModel,
    onClick: () -> Unit,
    marked: Boolean = false,
    onSwitch: (Boolean) -> Unit = {},
) {
    val switchedOn = row.switchedOn
    // Mood and Transparency have no screen, so their whole row is the switch (#121, #123).
    val wholeRowSwitches = switchedOn != null && !row.id.opensScreen()
    val needsAttention = LocalCopy.current.settings.needsAttention
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.transactionRow)
            .then(if (marked) Modifier.semantics { stateDescription = needsAttention } else Modifier)
            .then(
                if (wholeRowSwitches) {
                    Modifier.tappable(PtutRadii.r12, Role.Switch) { onSwitch(switchedOn != true) }
                } else {
                    Modifier.tappable(PtutRadii.r12, Role.Button, onClick = onClick)
                },
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.s12),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ptutIcon(row.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(Sizes.settingsGlyph))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = row.value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (marked) MaterialTheme.ptut.warnInk else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (marked) {
            // The red dot (K26): Sky's red means stale data, which is what this row can fix.
            Box(Modifier.size(Spacing.xs).clip(CircleShape).background(MaterialTheme.ptut.warn))
        }
        if (switchedOn != null) {
            SkySwitch(
                checked = switchedOn,
                onCheckedChange = if (wholeRowSwitches) null else onSwitch,
                label = row.name.takeUnless { wholeRowSwitches },
            )
        } else {
            Icon(PtutIcon.ChevronRight.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.settingsTrailing))
        }
    }
}
