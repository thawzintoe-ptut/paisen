package com.ptut.expense.ui.sky

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.orStill
import com.ptut.expense.design.ptutMotion
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.BACKSPACE
import com.ptut.expense.ui.NumpadRows

/**
 * Sky's keypad (p10, p13): twelve grey keys, the glyph in ink, a decimal point and a delete key on
 * the bottom row. It reports the key and holds nothing.
 */
@Composable
fun SkyNumpad(onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        NumpadRows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                row.forEach { key -> SkyKey(key = key, onKey = onKey, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** Down to 93% in 90ms and back in 140, as page 03 draws a key press: release slower than press. */
@Composable
private fun SkyKey(key: String, onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val held by interactions.collectIsPressedAsState()
    val motion = MaterialTheme.ptutMotion
    val scale by animateFloatAsState(
        targetValue = if (held) PRESSED_SCALE else 1f,
        animationSpec = tween(
            durationMillis = if (held) motion.keyDown.orStill() else motion.keyRelease.orStill(),
            easing = motion.standard,
        ),
        label = "sky key press",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = SkySizes.key)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(PtutRadii.r12)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button) { onKey(key) },
    ) {
        if (key == BACKSPACE) {
            Icon(
                imageVector = PtutIcon.Backspace.image,
                contentDescription = LocalCopy.current.shell.deleteLastDigit,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(SkySizes.barGlyph),
            )
        } else {
            Text(text = key, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private const val PRESSED_SCALE = 0.93f
