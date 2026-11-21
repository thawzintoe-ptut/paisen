package com.ptut.expense.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutType
import com.ptut.expense.ui.play.PlayFlipCard
import com.ptut.expense.ui.play.PlayMeter
import com.ptut.expense.ui.play.PlayStamp
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyListScreen

/** #88, p08: the card turns over, the stamp presses in, and the set's meter moves after it. */
@Composable
fun StampRevealView(
    uiState: RevealUiState,
    onTurn: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = (uiState as? RevealUiState.Loaded)?.title.orEmpty()
    SkyListScreen(title = title, onBack = onBack, modifier = modifier) {
        when (uiState) {
            RevealUiState.Loading -> Unit

            is RevealUiState.Missing -> item { PlayLine(uiState.line, Modifier.padding(horizontal = Spacing.md)) }

            is RevealUiState.Loaded -> {
                item {
                    Text(
                        text = uiState.hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md).padding(top = Spacing.xs)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(top = Spacing.md), contentAlignment = Alignment.Center) {
                        PlayFlipCard(
                            turned = uiState.turned,
                            onTurn = onTurn,
                            label = uiState.cardLabel,
                            front = { CardFront(uiState.hint) },
                            back = { CardBack(uiState) },
                        )
                    }
                }
                if (uiState.turned) {
                    item { SetCard(uiState.set, Modifier.padding(horizontal = Spacing.sm).padding(top = Spacing.s28)) }
                    item {
                        SkyButton(
                            label = uiState.done,
                            onClick = onDone,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.md, bottom = Spacing.xl),
                        )
                    }
                }
            }
        }
    }
}

/** p08's face-down side: a question mark and what to do. */
@Composable
private fun CardFront(hint: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(PtutIcon.Help.image, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(PlaySizes.aiDisc * 2))
        Text(hint, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CardBack(state: RevealUiState.Loaded) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
        PlayStamp(
            face = state.stamp.face,
            caption = state.stamp.caption,
            tone = state.stamp.tone,
            contentDescription = state.stamp.label,
            big = true,
            pressed = true,
        )
        Text(state.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        state.stamp.rareLine?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** How far the stamp's set has come, with the meter that moves after the turn. */
@Composable
internal fun SetCard(set: SetProgressUiModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PtutRadii.r20)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(set.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(set.count, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        PlayMeter(fraction = set.fraction, label = set.line)
        Text(set.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PlayLine(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}
