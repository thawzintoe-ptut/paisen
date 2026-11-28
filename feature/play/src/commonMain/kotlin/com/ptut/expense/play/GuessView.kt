package com.ptut.expense.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable
import com.ptut.expense.ui.play.PlayStamp
import com.ptut.expense.ui.play.StampTone
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyListScreen
import com.ptut.expense.ui.sky.SkyRollingNumber
import com.ptut.expense.ui.sky.SkyTextAction

/** #86, p01 then p02: the guess on the sky hero, then the reveal that rolls from it. */
@Composable
fun GuessView(
    uiState: GuessUiState,
    onNudge: (Int) -> Unit,
    onSet: (Int) -> Unit,
    onReveal: () -> Unit,
    onSeeWeek: (from: String, to: String) -> Unit,
    onBack: () -> Unit,
    /** A just-in-time tip shown above the feature, or null when none is due. */
    tip: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val title = when (uiState) {
        GuessUiState.Loading, is GuessUiState.Missing -> ""
        is GuessUiState.Guessing -> uiState.eyebrow
        is GuessUiState.Revealed -> uiState.eyebrow
    }
    SkyListScreen(title = title, onBack = onBack, modifier = modifier) {
        when (uiState) {
            GuessUiState.Loading -> Unit

            is GuessUiState.Missing -> {
                item { PlayLine(uiState.line, Modifier.padding(horizontal = Spacing.md)) }
                if (uiState.from.isNotEmpty()) {
                    item {
                        SkyButton(
                            label = uiState.seeWeek,
                            onClick = { onSeeWeek(uiState.from, uiState.to) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                        )
                    }
                }
            }

            is GuessUiState.Guessing -> {
                tip?.let { slot -> item { slot() } }
                item { Guessing(uiState, onNudge, onSet) }
                item {
                    SkyButton(label = uiState.guess, onClick = onReveal, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.md))
                }
                item {
                    SkyTextAction(
                        label = uiState.skip,
                        onClick = { onSeeWeek(uiState.from, uiState.to) },
                        enabled = true,
                        modifier = Modifier.padding(horizontal = Spacing.sm),
                    )
                }
                item { LocalLine(uiState.local) }
            }

            is GuessUiState.Revealed -> {
                item { Revealed(uiState) }
                item { How(uiState, Modifier.padding(horizontal = Spacing.sm).padding(top = Spacing.sm)) }
                item {
                    SkyButton(
                        label = uiState.seeWeek,
                        onClick = { onSeeWeek(uiState.from, uiState.to) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.md),
                    )
                }
                item { LocalLine(uiState.local) }
            }
        }
    }
}

@Composable
private fun Guessing(state: GuessUiState.Guessing, onNudge: (Int) -> Unit, onSet: (Int) -> Unit) {
    val colors = MaterialTheme.ptut
    // p01: the sky hero runs edge to edge; the slider and the record sit under it.
    Column(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().background(colors.heroBg).padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(state.title, style = MaterialTheme.typography.headlineSmall, color = colors.heroOn)
            Text(state.week, style = MaterialTheme.typography.bodyMedium, color = colors.heroOn)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Step(PtutIcon.TrendingFlat, state.less) { onNudge(-1) }
                Odometer(state.symbol, state.digits, state.spoken, startFrom = null, Modifier.weight(1f, fill = false))
                Step(PtutIcon.Add, state.more) { onNudge(1) }
            }
            Text(state.yourGuess, style = MaterialTheme.typography.labelLarge, color = colors.heroOn, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (state.paisenToo.isNotEmpty()) {
                PaiSeNLine(state.disc, state.paisenToo, colors.heroOn, Modifier.fillMaxWidth().padding(top = Spacing.xs), centered = true)
            }
        }
        Slider(
            value = state.index * 1f,
            onValueChange = { onSet(it.roundToStep()) },
            valueRange = 0f..(state.steps * 1f),
            steps = (state.steps - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.sm).semantics {
                contentDescription = state.sliderLabel
                stateDescription = state.spoken
            },
        )
        Text(
            state.record,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md),
        )
    }
}

/** PaiSeN speaking: its ink disc with P, then the line (p01, p07). */
@Composable
internal fun PaiSeNLine(disc: String, line: String, color: Color, modifier: Modifier = Modifier, centered: Boolean = false) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, if (centered) Alignment.CenterHorizontally else Alignment.Start),
    ) {
        PaiSeNDisc(disc)
        Text(line, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
internal fun PaiSeNDisc(disc: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(PlaySizes.aiDisc).clearAndSetSemantics { }.clip(CircleShape).background(MaterialTheme.colorScheme.primary),
    ) {
        Text(disc, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
    }
}

/** Halfway between two steps rounds to the nearer one. */
private fun Float.roundToStep(): Int = (this + HALF).toInt()

private const val HALF = 0.5f

@Composable
private fun Step(icon: PtutIcon, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(PlaySizes.step)
            .clearAndSetSemantics { contentDescription = label }
            .tappable(CircleShape, Role.Button, onClick = onClick)
            .clip(CircleShape)
            .background(MaterialTheme.ptut.heroInner),
    ) {
        Icon(icon.image, contentDescription = null, tint = MaterialTheme.ptut.heroOn, modifier = Modifier.size(Sizes.settingsGlyph))
    }
}

/** p01's odometer: each digit rolls on its own, the lower ones longer. */
@Composable
private fun Odometer(symbol: String, digits: String, spoken: String, startFrom: String?, modifier: Modifier = Modifier) {
    val style = MaterialTheme.ptutType.hero.copy(fontSize = 64.sp, letterSpacing = (-0.03).em)
    Row(modifier.clearAndSetSemantics { contentDescription = spoken }, verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, style = MaterialTheme.ptutType.heroSymbol, color = MaterialTheme.ptut.heroOn, modifier = Modifier.padding(end = Spacing.s6))
        SkyRollingNumber(text = digits, style = style, color = MaterialTheme.ptut.heroOn, startFrom = startFrom)
    }
}

@Composable
private fun Revealed(state: GuessUiState.Revealed) {
    val colors = MaterialTheme.ptut
    Column(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().background(colors.heroBg).padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            Text(state.title, style = MaterialTheme.typography.titleMedium, color = colors.heroOn)
            Text(state.week, style = MaterialTheme.typography.bodySmall, color = colors.heroOn)
            Odometer(state.symbol, state.digits, "${state.symbol}${state.digits}", startFrom = state.startFrom)
            Column(Modifier.fillMaxWidth().clip(PtutRadii.r16).background(colors.heroInner)) {
                VersusRow(state.you, state.closerLabel) { YouDisc(state.you.who) }
                VersusRow(state.paisen, state.closerLabel) { PaiSeNDisc(state.disc) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (state.youWon && state.stampFace.isNotEmpty()) {
                PlayStamp(face = state.stampFace, caption = state.stampCaption, tone = StampTone.Sky, contentDescription = state.stampLabel, pressed = true)
            }
            Text(state.outcome, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun YouDisc(who: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(PlaySizes.aiDisc).clip(CircleShape).background(MaterialTheme.ptut.heroOn),
    ) {
        Text(who, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.ptut.heroBg, maxLines = 1)
    }
}

@Composable
private fun VersusRow(row: GuessRowUiModel, closerLabel: String, lead: @Composable () -> Unit) {
    val colors = MaterialTheme.ptut
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = PlaySizes.versusRow).padding(horizontal = Spacing.s12, vertical = Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s10),
    ) {
        lead()
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s6)) {
                Text(row.who, style = MaterialTheme.typography.titleSmall, color = colors.heroOn)
                if (row.closer) {
                    Text(
                        text = closerLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = Spacing.s10, vertical = Spacing.xxs),
                    )
                }
            }
            Text(row.off, style = MaterialTheme.typography.bodySmall, color = colors.heroOn)
        }
        Text(row.amount, style = MaterialTheme.typography.titleMedium, color = colors.heroOn)
    }
}

@Composable
private fun How(state: GuessUiState.Revealed, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().clip(PtutRadii.r20).background(MaterialTheme.colorScheme.tertiaryContainer).padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(state.how, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        state.howLines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
