package com.ptut.expense.ui.sky

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.inMillis
import com.ptut.expense.design.ptutMotion
import com.ptut.expense.design.ptutReduceMotion

/**
 * #200: a figure or a line that changes slides up into place and fades, so the eye sees that it
 * changed. With reduce motion on it simply shows the new value (the motion page's rule).
 */
@Composable
fun <T> SkyChange(target: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val still = ptutReduceMotion
    val duration = MaterialTheme.ptutMotion.toastRise.inMillis
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            if (still) {
                ContentTransform(EnterTransition.None, ExitTransition.None)
            } else {
                (slideInVertically(tween(durationMillis = duration)) { it / 2 } + fadeIn(tween(durationMillis = duration)))
                    .togetherWith(slideOutVertically(tween(durationMillis = duration)) { -it / 2 } + fadeOut(tween(durationMillis = duration)))
            }
        },
        label = "sky-change",
    ) { content(it) }
}

/** #200: a list row that fades in, fades out and slides to its new place, or just appears. */
@Composable
fun LazyItemScope.skyItem(): Modifier {
    if (ptutReduceMotion) return Modifier
    val duration = MaterialTheme.ptutMotion.chip.inMillis
    return Modifier.animateItem(
        fadeInSpec = tween(durationMillis = duration),
        placementSpec = tween(durationMillis = MaterialTheme.ptutMotion.push.inMillis),
        fadeOutSpec = tween(durationMillis = duration),
    )
}

/**
 * #200: something is on its way (sign-in, join, a link). Always beside words that say what, so
 * with reduce motion on, where the ring is not drawn, nothing is lost.
 */
@Composable
fun SkyProgress(modifier: Modifier = Modifier) {
    if (ptutReduceMotion) return
    CircularProgressIndicator(
        modifier = modifier.size(SkySizes.progress),
        strokeWidth = SkySizes.progressStroke,
        color = MaterialTheme.colorScheme.primary,
    )
}
