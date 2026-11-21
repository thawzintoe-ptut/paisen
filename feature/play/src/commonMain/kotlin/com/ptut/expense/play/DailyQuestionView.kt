package com.ptut.expense.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutIcon
import com.ptut.expense.design.tappable
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyListScreen

/** #87, p07: two cards; one tap answers; the amounts and the sky card show after it. */
@Composable
fun DailyQuestionView(
    uiState: QuestionUiState,
    onPick: (Boolean) -> Unit,
    onOpenStamp: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (uiState) {
        QuestionUiState.Loading -> ""
        is QuestionUiState.None -> uiState.title
        is QuestionUiState.Asking -> uiState.title
        is QuestionUiState.Answered -> uiState.title
    }
    SkyListScreen(title = title, onBack = onBack, modifier = modifier) {
        when (uiState) {
            QuestionUiState.Loading -> Unit

            is QuestionUiState.None -> item { PlayLine(uiState.line, Modifier.padding(horizontal = Spacing.md)) }

            is QuestionUiState.Asking -> {
                item { Ask(uiState.ask, uiState.disc, uiState.picked) }
                item { Picks(uiState.a, uiState.b, enabled = true, onPick = onPick) }
                item { LocalLine(uiState.local) }
            }

            is QuestionUiState.Answered -> {
                item { Ask(uiState.ask, uiState.disc, uiState.picked) }
                item { Picks(uiState.a, uiState.b, enabled = false, onPick = {}) }
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = Spacing.md).semantics { liveRegion = LiveRegionMode.Polite },
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(uiState.verdict, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(uiState.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(uiState.freezeLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                uiState.stampId?.let { id ->
                    item {
                        SkyButton(
                            label = uiState.stampLabel,
                            onClick = { onOpenStamp(id) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.xs),
                        )
                    }
                }
                item { PlayLine(uiState.end, Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.xs)) }
                item { LocalLine(uiState.local) }
            }
        }
    }
}

/** p07: the question on the sky hero, edge to edge, with PaiSeN's line under it. */
@Composable
private fun Ask(ask: String, disc: String, picked: String) {
    val colors = MaterialTheme.ptut
    Column(
        modifier = Modifier.fillMaxWidth().background(colors.heroBg).padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        Text(ask, style = MaterialTheme.typography.headlineSmall, color = colors.heroOn)
        if (picked.isNotEmpty()) PaiSeNLine(disc, picked, colors.heroOn)
    }
}

@Composable
private fun Picks(a: QuestionCardUiModel, b: QuestionCardUiModel, enabled: Boolean, onPick: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm).padding(top = Spacing.s20),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        PickCard(a, enabled, { onPick(true) }, Modifier.weight(1f))
        PickCard(b, enabled, { onPick(false) }, Modifier.weight(1f))
    }
}

@Composable
private fun PickCard(card: QuestionCardUiModel, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .heightIn(min = PlaySizes.pick)
            .clearAndSetSemantics { contentDescription = card.label }
            .tappable(PtutRadii.r20, Role.Button, enabled = enabled, onClick = onClick)
            .clip(PtutRadii.r20)
            .background(if (card.right) MaterialTheme.ptut.accent else scheme.tertiaryContainer)
            .border(
                width = if (card.picked) PlaySizes.dayRing else PlaySizes.flipEdge,
                color = if (card.picked) scheme.primary else scheme.surfaceContainerHigh,
                shape = PtutRadii.r20,
            )
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.s6),
    ) {
        Icon(ptutIcon(card.icon), contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(Sizes.settingsGlyph))
        Text(card.name, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 2)
        card.amount?.let {
            Text(it, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 30.sp, fontWeight = FontWeight.Light), color = scheme.onSurface, maxLines = 1)
        }
        card.count?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
    }
}

/** R8: "Worked out on this phone", under every PaiSeN line. */
@Composable
internal fun LocalLine(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = Spacing.md).padding(top = Spacing.s12),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s6),
    ) {
        Icon(ptutIcon("smartphone"), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.statusGlyph))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
