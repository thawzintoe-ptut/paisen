package com.ptut.expense.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A tap target whose ripple takes the shape it is drawn in.
 *
 * `Modifier.clickable` draws its indication in the node's own rectangle. On a rounded control that
 * clips nothing, that is a rectangle with the control's corners hanging out of it — the ripple
 * says the target is square when it is not. Clipping first is the whole fix, and the ordering is
 * the part that is easy to get wrong: a `clip` *after* the `clickable` clips the content and
 * leaves the indication exactly where it was.
 *
 * Two more states every target needs (UX pass, Nov 2026):
 * - **Focus.** A keyboard, D-pad or switch user sees a 2dp ring in the theme's ink, 2dp outside the
 *   shape so it shows on an ink fill too (WCAG 2.4.7). Touch never focuses a clickable, so a finger
 *   never sees it.
 * - **Press.** [pressScale] below 1 lets a compact control give under the finger, on top of the
 *   ripple. Rows keep 1: a whole row shrinking reads as a glitch. Off under reduce motion.
 */
fun Modifier.tappable(
    shape: Shape,
    role: Role? = null,
    enabled: Boolean = true,
    pressScale: Float = 1f,
    onClick: () -> Unit,
): Modifier = composed {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val still = ptutReduceMotion || pressScale == 1f
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !still) pressScale else 1f,
        animationSpec = if (still) snap() else spring(dampingRatio = PRESS_DAMPING, stiffness = PRESS_STIFFNESS),
        label = "press",
    )
    var focused by remember { mutableStateOf(false) }
    val ring = MaterialTheme.colorScheme.primary

    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
        // Drawn before the clip, so the ring can sit outside the shape.
        .drawWithContent {
            drawContent()
            if (focused) {
                val width = FOCUS_RING_WIDTH.toPx()
                val grow = FOCUS_RING_GAP.toPx() + width / 2
                val outline = shape.createOutline(Size(size.width + grow * 2, size.height + grow * 2), layoutDirection, this)
                translate(-grow, -grow) { drawOutline(outline, ring, style = Stroke(width)) }
            }
        }
        .onFocusChanged { focused = it.isFocused }
        .clip(shape)
        .clickable(
            interactionSource = interactions,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

/** How far a compact control gives under a finger: 0.96, inside the 0.95–1.05 press range. */
const val PRESS_SCALE_COMPACT = 0.96f

private val FOCUS_RING_WIDTH = 2.dp
private val FOCUS_RING_GAP = 2.dp

/** Quick and only just springy: the press answers inside ~100ms and settles without a wobble. */
private const val PRESS_DAMPING = 0.8f
private const val PRESS_STIFFNESS = 1500f
