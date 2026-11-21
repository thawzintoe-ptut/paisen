package com.ptut.expense.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutType
import com.ptut.expense.ui.play.PlayMeter
import com.ptut.expense.ui.sky.SkyListScreen
import com.ptut.expense.ui.sky.SkyRollingNumber

/** #99 (p21): the score rolls when it changes; each part has its meter and the one action that adds to it. */
@Composable
fun MoneyScoreView(uiState: MoneyScoreUiState?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SkyListScreen(title = uiState?.title.orEmpty(), onBack = onBack, modifier = modifier) {
        uiState ?: return@SkyListScreen
        item {
            val colors = MaterialTheme.ptut
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm)
                    .clip(PtutRadii.r20)
                    .background(colors.heroBg)
                    .padding(Spacing.md)
                    .clearAndSetSemantics { contentDescription = "${uiState.score} ${uiState.outOf}" },
                horizontalAlignment = Alignment.Start,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(uiState.fraction, track = colors.heroInner, fill = MaterialTheme.colorScheme.primary) {
                        SkyRollingNumber(text = uiState.score, style = MaterialTheme.ptutType.hero, color = colors.heroOn)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(uiState.word, style = MaterialTheme.typography.titleLarge, color = colors.heroOn)
                        Text(uiState.outOf, style = MaterialTheme.typography.bodyMedium, color = colors.heroOn)
                    }
                }
            }
        }
        uiState.none?.let { line -> item { PlayLine(line, Modifier.padding(horizontal = Spacing.md)) } }
        uiState.parts.forEach { part ->
            item(key = part.name) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm).clip(PtutRadii.r20).background(MaterialTheme.colorScheme.tertiaryContainer).padding(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(part.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Text(part.points, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                    PlayMeter(fraction = part.fraction, label = "${part.name}, ${part.points}")
                    Text("${part.action} · ${part.gain}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { PlayLine(uiState.note, Modifier.padding(horizontal = Spacing.md)) }
    }
}

/** p21's ring: a track all round, ink filled clockwise from the top as far as the score. */
@Composable
private fun ScoreRing(fraction: Float, track: Color, fill: Color, content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(PlaySizes.scoreRing)) {
        Canvas(Modifier.size(PlaySizes.scoreRing)) {
            val stroke = PlaySizes.scoreStroke.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, startAngle = 0f, sweepAngle = FULL_TURN, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            drawArc(fill, startAngle = RING_START, sweepAngle = FULL_TURN * fraction.coerceIn(0f, 1f), useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}

private const val FULL_TURN = 360f
private const val RING_START = -90f
