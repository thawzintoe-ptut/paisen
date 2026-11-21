package com.ptut.expense.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.cardAngleAt
import com.ptut.expense.design.orStill
import com.ptut.expense.design.ptutMotion
import com.ptut.expense.design.showsBack
import com.ptut.expense.design.tappable

/** How far the camera sits, in card widths, so the turn reads as depth and not a squeeze. */
private const val CAMERA_DISTANCE = 12f

/**
 * A card that turns over on Y when tapped (#84). The front is ink with its stripe, the back the
 * grey card with a hairline edge. Only [turned] decides which side shows, so a card turned before
 * a process death comes back turned; reduced motion shows the back at once.
 */
@Composable
fun PlayFlipCard(
    turned: Boolean,
    onTurn: () -> Unit,
    label: String,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.ptutMotion
    val turnMillis = motion.cardTurn.orStill()
    val progress = remember { Animatable(if (turned) 1f else 0f) }
    LaunchedEffect(turned) {
        val goal = if (turned) 1f else 0f
        if (turnMillis == 0) progress.snapTo(goal) else progress.animateTo(goal, tween(durationMillis = turnMillis, easing = motion.linear))
    }
    // The angle is read inside the layer, so the turn redraws each frame without recomposing the card.
    val showingBack by remember(motion) { derivedStateOf { showsBack(cardAngleAt(progress.value, motion.turn)) } }
    val density = LocalDensity.current.density
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(PlaySizes.flipWidth, PlaySizes.flipHeight)
            .semantics { contentDescription = label }
            .tappable(PtutRadii.r28, Role.Button, enabled = !turned, onClick = onTurn)
            .graphicsLayer {
                rotationY = cardAngleAt(progress.value, motion.turn)
                cameraDistance = CAMERA_DISTANCE * density
            }
            .clip(PtutRadii.r28)
            .background(if (showingBack) scheme.tertiaryContainer else scheme.primary)
            .then(if (showingBack) Modifier.border(PlaySizes.flipEdge, scheme.surfaceContainerHigh, PtutRadii.r28) else Modifier),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            // The back is drawn turned once more, so its words do not read in a mirror.
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { if (showingBack) rotationY = 180f }
                .padding(Spacing.sm),
        ) {
            if (showingBack) back() else front()
        }
    }
}
