package com.ptut.expense.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.orStill
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutMotion
import com.ptut.expense.design.tappable
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyListScreen
import com.ptut.expense.ui.sky.SkySwitch
import kotlin.math.cos
import kotlin.math.sin

/** Whole turns before the wheel settles, so a spin reads as a spin. */
private const val WHEEL_TURNS = 4

private const val FULL_TURN = 360f

/** The slices start under the pointer, at the top. */
private const val TOP = -90f

/** Names sit a little past halfway out, as on p11. */
private const val LABEL_RADIUS = 0.57f
private const val DEG_TO_RAD = 0.017453292f

/** #94 (p11): the wheel spins on tap only and stops on the result (D13); reduced motion shows it at once. */
@Composable
fun WhoPaysWheelView(
    uiState: WheelUiState,
    onSpin: () -> Unit,
    onFair: (Boolean) -> Unit,
    onAdd: (payerMemberId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The Fair wheel tip, under the wheel and pointing at the switch; null when none is due. */
    tip: (@Composable () -> Unit)? = null,
) {
    val state = uiState as? WheelUiState.Loaded
    val palette = wheelColors()
    SkyListScreen(title = state?.title.orEmpty(), onBack = onBack, modifier = modifier) {
        state ?: return@SkyListScreen
        state.usual?.let { usual ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(PlaySizes.aiDisc).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Text("P", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Text(usual, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        item { SpinningWheel(state, palette) }
        state.tooFew?.let { line -> item { Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.md)) } }
        item { FairRow(state, onFair) }
        tip?.let { slot -> item { slot() } }
        if (state.tooFew == null) {
            item { SkyButton(state.spinLabel, onClick = onSpin, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) }
        }
        state.result?.let { result ->
            item {
                SkyButton(
                    result.addLabel,
                    onClick = { onAdd(result.memberId) },
                    kind = SkyButtonKind.Outline,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun FairRow(state: WheelUiState.Loaded, onFair: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm)
            .clip(PtutRadii.r20)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .heightIn(min = SkySizes.transactionRow)
            .tappable(PtutRadii.r20, Role.Switch) { onFair(!state.fair) }
            .padding(horizontal = Spacing.sm, vertical = Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s14),
    ) {
        Column(Modifier.weight(1f)) {
            Text(state.fairLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(state.fairLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SkySwitch(checked = state.fair, onCheckedChange = null)
    }
}

@Composable
private fun SpinningWheel(state: WheelUiState.Loaded, palette: List<Color>) {
    val motion = MaterialTheme.ptutMotion
    val spinMillis = motion.wheelSpin.orStill()
    val rotation = remember { Animatable(0f) }
    // The turn shown last, so coming back to the screen does not spin it again.
    var settled by rememberSaveable { mutableIntStateOf(0) }
    val result = state.result
    LaunchedEffect(result?.turn) {
        if (result == null) return@LaunchedEffect
        val base = rotation.value - rotation.value % FULL_TURN
        val target = base + WHEEL_TURNS * FULL_TURN + (FULL_TURN - result.spin * FULL_TURN)
        // Back from a rotation or a backstack pop, the turn is settled but the wheel is fresh: show the result without spinning.
        if (result.turn == settled) {
            if (rotation.value == 0f) rotation.snapTo(target)
            return@LaunchedEffect
        }
        if (spinMillis == 0) rotation.snapTo(target) else rotation.animateTo(target, tween(durationMillis = spinMillis, easing = motion.emphasized))
        settled = result.turn
    }
    val total = state.slices.sumOf { it.weight }.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
        val ink = MaterialTheme.colorScheme.primary
        val nameStyle = MaterialTheme.typography.titleSmall
        val measurer = rememberTextMeasurer()
        val labels = state.slices.mapIndexed { index, slice ->
            val fill = palette.sliceColor(index, state.slices.size)
            measurer.measure(slice.name, nameStyle.copy(color = if (fill == ink) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface), maxLines = 1)
        }
        Box(Modifier.size(PlaySizes.wheel), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(PlaySizes.wheel).graphicsLayer { rotationZ = rotation.value }) {
                val ring = PlaySizes.stampRing.toPx()
                val radius = size.minDimension / 2 - ring
                var start = TOP
                state.slices.forEachIndexed { index, slice ->
                    val sweep = FULL_TURN * slice.weight / total
                    drawArc(color = palette.sliceColor(index, state.slices.size), startAngle = start, sweepAngle = sweep, useCenter = true, topLeft = Offset(ring, ring), size = Size(radius * 2, radius * 2))
                    val mid = (start + sweep / 2) * DEG_TO_RAD
                    val label = labels[index]
                    val at = Offset(center.x + radius * LABEL_RADIUS * cos(mid), center.y + radius * LABEL_RADIUS * sin(mid))
                    drawText(label, topLeft = Offset(at.x - label.size.width / 2, at.y - label.size.height / 2))
                    start += sweep
                }
                drawCircle(ink, radius = radius, style = Stroke(width = ring))
                drawCircle(ink, radius = PlaySizes.wheelHub.toPx())
            }
            Canvas(Modifier.size(PlaySizes.wheel)) {
                val w = PlaySizes.wheelPointer.toPx()
                val pointer = Path().apply {
                    moveTo(center.x - w / 2, 0f)
                    lineTo(center.x + w / 2, 0f)
                    lineTo(center.x, w)
                    close()
                }
                drawPath(pointer, ink)
            }
        }
        if (result != null && settled == result.turn) {
            Text(
                text = result.line,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.sm).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** The last slice meets the first, so it skips the first's colour when the turn would repeat it. */
private fun List<Color>.sliceColor(index: Int, count: Int): Color =
    if (index == count - 1 && count > 1 && index % size == 0) this[1] else this[index % size]

/** Ink, sky and the hero's inner tint, in turn, so no two neighbours match. Never ruby: nobody owes here. */
@Composable
private fun wheelColors(): List<Color> = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.ptut.accent, MaterialTheme.ptut.heroInner)
