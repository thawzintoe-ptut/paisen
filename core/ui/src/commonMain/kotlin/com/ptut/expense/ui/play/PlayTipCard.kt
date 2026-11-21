package com.ptut.expense.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.orStill
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutMotion
import com.ptut.expense.design.ptutReduceMotion
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable

/** One just-in-time tip's words, as the ViewModel hands them over. */
data class TipUiModel(val eyebrow: String, val title: String, val body: String, val skip: String, val gotIt: String, val label: String)

/** Where the tip's arrow points: down at the feature under it, or up at the one above it. */
enum class TipPointer { Down, Up }

/**
 * A just-in-time tip (V3 section 4, `.pl-tip`): ink card, eyebrow, title, body, Skip all tips and
 * Got it. It slides up into place; nothing fades, and under reduced motion it is simply there.
 */
@Composable
fun PlayTipCard(
    tip: TipUiModel,
    onGotIt: () -> Unit,
    onSkipAll: () -> Unit,
    modifier: Modifier = Modifier,
    pointer: TipPointer = TipPointer.Down,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.ptutMotion
    val still = ptutReduceMotion
    val slide = remember { Animatable(if (still) 0f else 1f) }
    val slideMillis = motion.tipIn.orStill()
    LaunchedEffect(still) { if (!still) slide.animateTo(0f, tween(durationMillis = slideMillis, easing = motion.standard)) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = PlaySizes.tipSlide.toPx() * slide.value },
    ) {
        val arrow = Modifier
            .padding(start = PlaySizes.tipArrowInset)
            .size(PlaySizes.tipArrow)
            .rotate(ARROW_TURN)
            .background(scheme.primary)
        if (pointer == TipPointer.Up) Box(arrow.align(Alignment.TopStart).offset(y = -PlaySizes.tipArrow / 2))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PtutRadii.r18)
                .background(scheme.primary)
                .semantics {
                    paneTitle = tip.label
                    liveRegion = LiveRegionMode.Polite
                }
                .padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.s14, bottom = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(tip.eyebrow.uppercase(), style = MaterialTheme.ptutType.eyebrow, color = scheme.onPrimary.copy(alpha = SOFT))
            Text(tip.title, style = MaterialTheme.typography.titleMedium, color = scheme.onPrimary)
            Text(tip.body, style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.heightIn(min = PlaySizes.step).tappable(PtutRadii.r12, Role.Button, onClick = onSkipAll).padding(horizontal = Spacing.xs),
                ) {
                    Text(tip.skip, style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary.copy(alpha = SOFT))
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = PlaySizes.step)
                        .clip(CircleShape)
                        .background(MaterialTheme.ptut.accent)
                        .tappable(PtutRadii.r28, Role.Button, onClick = onGotIt)
                        .padding(horizontal = Spacing.sm),
                ) {
                    Text(tip.gotIt, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                }
            }
        }
        if (pointer == TipPointer.Down) Box(arrow.align(Alignment.BottomStart).offset(y = PlaySizes.tipArrow / 2))
    }
}

private const val ARROW_TURN = 45f

/** The eyebrow and Skip sit back a step from the title, as `.pl-tip` draws them. */
private const val SOFT = 0.75f
