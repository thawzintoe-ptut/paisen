package com.ptut.expense.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.STAMP_PRESS_FROM
import com.ptut.expense.design.STAMP_TILT
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.orStill
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutMotion
import com.ptut.expense.design.ptutReduceMotion
import com.ptut.expense.design.stampScaleAt
import kotlinx.coroutines.delay

/** Ink on the grey card, sky-filled for a rare one, or the outline of a stamp still to earn. */
enum class StampTone { Ink, Sky, Empty }

/**
 * An ink ring turned [STAMP_TILT]° with its [face] and a small [caption] (#84). The first time it
 * shows [pressed], it lands from 1.35× after the card under it has turned; under reduced motion it
 * is simply there. An empty slot is a quiet outline, never turned.
 */
@Composable
fun PlayStamp(
    face: String,
    caption: String,
    tone: StampTone,
    contentDescription: String,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    pressed: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.ptutMotion
    val still = ptutReduceMotion
    val pressMillis = motion.stampPress.orStill()
    var landed by rememberSaveable { mutableStateOf(!pressed || still) }
    val progress = remember { Animatable(if (landed) 1f else 0f) }
    LaunchedEffect(pressed, still) {
        if (!pressed || landed) return@LaunchedEffect
        if (!still) delay(motion.stampPressDelay)
        progress.animateTo(1f, tween(durationMillis = pressMillis, easing = motion.linear))
        landed = true
    }
    val empty = tone == StampTone.Empty
    val ink = if (empty) scheme.onSurfaceVariant else scheme.primary
    val fill = when (tone) {
        StampTone.Ink -> scheme.tertiaryContainer
        StampTone.Sky -> MaterialTheme.ptut.accent
        StampTone.Empty -> scheme.surface
    }
    val size = if (big) PlaySizes.stampBig else PlaySizes.stamp
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { this.contentDescription = contentDescription }
            .scale(if (landed) 1f else stampScaleAt(progress.value, motion.press).coerceAtMost(STAMP_PRESS_FROM))
            .rotate(if (empty) 0f else STAMP_TILT)
            .clip(CircleShape)
            .background(fill)
            .border(if (empty) PlaySizes.stampEmptyRing else PlaySizes.stampRing, if (empty) scheme.surfaceContainerHigh else ink, CircleShape),
    ) {
        if (!empty) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(PlaySizes.stampInnerInset)
                    .border(PlaySizes.stampInnerRing, ink, CircleShape),
            )
        }
        // Stamp art: its text scales with the stamp, not with the phone's text size, so it never spills.
        val density = LocalDensity.current
        // A face longer than four letters ("Sealed") steps down so it stays inside the ring.
        val faceBase = with(density) { (if (big) PlaySizes.stampFaceBig else PlaySizes.stampFace).toSp() }
        val faceSize = if (face.length > SHORT_FACE) faceBase * SHORT_FACE / face.length else faceBase
        val captionSize = with(density) { (if (big) PlaySizes.stampCaptionBig else PlaySizes.stampCaption).toSp() }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = PlaySizes.stampInnerInset),
        ) {
            Text(
                text = face,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = faceSize, lineHeight = faceSize * FACE_LINE, fontWeight = FontWeight.Medium),
                color = ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = caption.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = captionSize, lineHeight = captionSize * CAPTION_LINE, letterSpacing = CAPTION_SPACING.em),
                color = ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

private const val FACE_LINE = 1.1f
private const val SHORT_FACE = 4
private const val CAPTION_LINE = 1.2f
private const val CAPTION_SPACING = 0.04f
