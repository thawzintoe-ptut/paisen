package com.ptut.expense.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.inMillis
import com.ptut.expense.design.orStill
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutMotion
import com.ptut.expense.design.ptutReduceMotion
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyEditableKeyValueRow
import com.ptut.expense.ui.sky.SkyGroupedCard
import com.ptut.expense.ui.sky.SkyListScreen
import com.ptut.expense.ui.sky.SkyRollingAmount
import kotlinx.coroutines.launch
import kotlin.math.sin

private const val TWO_PI = 6.2831855f

private const val PI_F = 3.1415927f

/** p06's artboard for the bottle, the inside's top and bottom, and its wave, in artboard units. */
private const val JAR_W = 200f
private const val JAR_H = 250f
private const val WATER_TOP = 46f
private const val WATER_BOTTOM = 240f
private const val WAVE_LENGTH = 100f

/** How high a wave rises at rest and the rise a tap adds; the back wave sits higher and out of step. */
private const val IDLE_WAVE = 4f
private const val SLOSH_WAVE = 12f
private const val BACK_RISE = 6f
private const val BACK_SHIFT = 1.3f

/** A tap tilts the water up to 11° and rocks the jar up to 4°, three swings that die away. */
private const val WATER_TILT = 11f
private const val JAR_ROCK = 4f
private const val ROCK_SWINGS = 3f

/** Points along the water's surface. */
private const val WAVE_STEPS = 32

/** #92 (p06): name and amount first; then the jar, its pace, and what skipping a habit would do. */
@Composable
fun GoalJarView(
    uiState: GoalJarUiState?,
    onName: (String) -> Unit,
    onTarget: (String) -> Unit,
    onSave: () -> Unit,
    onWhatIf: (String) -> Unit,
    onArchive: () -> Unit,
    onBack: () -> Unit,
    /** A just-in-time tip shown above the feature, or null when none is due. */
    tip: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val title = when (uiState) {
        is GoalJarUiState.Editing -> uiState.title
        is GoalJarUiState.Jar -> uiState.title
        GoalJarUiState.Missing, null -> ""
    }
    SkyListScreen(title = title, onBack = onBack, modifier = modifier) {
        when (uiState) {
            null, GoalJarUiState.Missing -> Unit

            is GoalJarUiState.Editing -> {
                item {
                    Column(Modifier.padding(horizontal = Spacing.md)) {
                        SkyEditableKeyValueRow(label = uiState.nameLabel, value = uiState.name, onValueChange = onName, placeholder = uiState.nameLabel)
                        SkyEditableKeyValueRow(label = "${uiState.targetLabel} ${uiState.symbol}", value = uiState.target, onValueChange = onTarget, placeholder = "0")
                    }
                }
                item { SkyButton(uiState.save, onClick = onSave, enabled = uiState.canSave, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) }
                item { PlayLine(uiState.noMoney, Modifier.padding(horizontal = Spacing.md)) }
            }

            is GoalJarUiState.Jar -> {
                tip?.let { slot -> item { slot() } }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.name.uppercase(), style = MaterialTheme.ptutType.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        GoalJarFill(uiState.fraction, uiState.label, Modifier.padding(top = Spacing.md))
                        SkyRollingAmount(
                            symbol = uiState.saved.symbol,
                            whole = uiState.saved.whole,
                            fraction = uiState.saved.fraction,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = Spacing.md).clearAndSetSemantics { contentDescription = "${uiState.saved.symbol}${uiState.saved.whole}${uiState.saved.fraction} ${uiState.ofTarget}" },
                        )
                        Text(uiState.ofTarget, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
                    }
                }
                item {
                    SkyGroupedCard(
                        rows = listOf(
                            { JarRow(uiState.keptTitle, uiState.keptLine, uiState.kept) },
                            { JarRow(uiState.paceTitle, uiState.pace, null) },
                        ),
                        modifier = Modifier.padding(horizontal = Spacing.sm),
                    )
                }
                uiState.whatIf.forEach { what ->
                    item(key = what.categoryId) {
                        val chosen = what.categoryId == uiState.chosen
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.sm)
                                .tappable(PtutRadii.r18, Role.Button) { onWhatIf(what.categoryId) }
                                .clip(PtutRadii.r18)
                                .background(if (chosen) MaterialTheme.ptut.accent else MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = Spacing.sm, vertical = Spacing.s12),
                        ) {
                            Text(what.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            if (chosen) Text(what.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                item { PlayLine(uiState.noMoney, Modifier.padding(horizontal = Spacing.md)) }
                item { SkyButton(uiState.archive, onClick = onArchive, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) }
            }
        }
    }
}

/** A row on the jar's card (p06): what it is, a line under it, and its value on the right. */
@Composable
private fun JarRow(title: String, line: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = Spacing.sm, vertical = Spacing.s12),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * The jar (D13), drawn from p06's bottle: a lid, a neck and a round body. Two waves cross on the
 * water all the time while it is on screen; a tap sloshes it and rocks the jar, then both settle.
 * Under reduced motion the water lies flat and still.
 */
@Composable
private fun GoalJarFill(fraction: Float, label: String, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.ptutMotion
    val still = ptutReduceMotion
    val scope = rememberCoroutineScope()
    val sloshMillis = motion.jarSlosh.orStill()
    // No transition at all while still: an endless tween, even of nothing, keeps the frame clock awake.
    val phase = if (still) {
        0f
    } else {
        rememberInfiniteTransition(label = "jar water").animateFloat(
            initialValue = 0f,
            targetValue = TWO_PI,
            animationSpec = infiniteRepeatable(tween(durationMillis = motion.jarIdle.inMillis, easing = motion.linear), RepeatMode.Restart),
            label = "jar phase",
        ).value
    }
    val slosh = remember { Animatable(0f) }
    val front = MaterialTheme.ptut.accent
    val back = MaterialTheme.ptut.barTrack
    val glass = MaterialTheme.colorScheme.tertiaryContainer
    val ink = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(PlaySizes.jarWidth, PlaySizes.jarHeight)
            .clearAndSetSemantics { contentDescription = label }
            .tappable(PtutRadii.r28, Role.Button) {
                if (still) return@tappable
                scope.launch {
                    slosh.snapTo(1f)
                    slosh.animateTo(0f, tween(durationMillis = sloshMillis, easing = motion.standard))
                }
            },
    ) {
        Canvas(
            Modifier
                .size(PlaySizes.jarWidth, PlaySizes.jarHeight)
                .graphicsLayer { rotationZ = JAR_ROCK * rock(slosh.value) },
        ) {
            val sx = size.width / JAR_W
            val sy = size.height / JAR_H
            val line = PlaySizes.stampRing.toPx()
            val lid = Path().apply {
                addRoundRect(RoundRect(70f * sx, 8f * sy, 130f * sx, 30f * sy, CornerRadius(6f * sx, 6f * sy)))
            }
            val body = bottle(sx, sy, inset = 0f)
            val inside = bottle(sx, sy, inset = 2f)
            drawPath(lid, glass)
            drawPath(lid, ink, style = Stroke(width = line))
            drawPath(body, glass)
            clipPath(inside) {
                val top = WATER_TOP * sy
                val bottom = WATER_BOTTOM * sy
                val level = bottom - (bottom - top) * fraction.coerceIn(0f, 1f)
                val lift = if (still) 0f else (IDLE_WAVE + SLOSH_WAVE * slosh.value) * sy
                rotate(degrees = if (still) 0f else WATER_TILT * rock(slosh.value), pivot = Offset(size.width / 2, level)) {
                    drawPath(water(level - BACK_RISE * sy, lift, -phase + BACK_SHIFT, sx), back)
                    drawPath(water(level, lift, phase, sx), front)
                }
            }
            drawPath(body, ink, style = Stroke(width = line))
        }
    }
}

/** p06's bottle in its 200 × 250 artboard, scaled; [inset] pulls the outline in for the water's clip. */
private fun bottle(sx: Float, sy: Float, inset: Float): Path = Path().apply {
    val i = inset
    moveTo((60f + i) * sx, (30f + i) * sy)
    lineTo((140f - i) * sx, (30f + i) * sy)
    lineTo((140f - i) * sx, 44f * sy)
    cubicTo(162f * sx, 54f * sy, (176f - i) * sx, 74f * sy, (176f - i) * sx, 100f * sy)
    lineTo((176f - i) * sx, 218f * sy)
    cubicTo((176f - i) * sx, 232f * sy, 165f * sx, (242f - i) * sy, 152f * sx, (242f - i) * sy)
    lineTo(48f * sx, (242f - i) * sy)
    cubicTo(35f * sx, (242f - i) * sy, (24f + i) * sx, 232f * sy, (24f + i) * sx, 218f * sy)
    lineTo((24f + i) * sx, 100f * sy)
    cubicTo((24f + i) * sx, 74f * sy, 38f * sx, 54f * sy, (60f + i) * sx, 44f * sy)
    close()
}

/** The water below a wave at [level]: two crests across the jar, as p06's 100-wide wave. */
private fun DrawScope.water(level: Float, lift: Float, phase: Float, sx: Float): Path = Path().apply {
    val wavelength = WAVE_LENGTH * sx
    val reach = size.width
    moveTo(-reach, size.height * 2)
    var x = -reach
    while (x <= size.width + reach) {
        lineTo(x, level + lift * sin(phase + TWO_PI * x / wavelength))
        x += size.width / WAVE_STEPS
    }
    lineTo(size.width + reach, size.height * 2)
    close()
}

/** 1 at the tap, 0 at rest: a swing that changes side three times and dies away. */
private fun rock(slosh: Float): Float = slosh * sin((1f - slosh) * ROCK_SWINGS * PI_F)
