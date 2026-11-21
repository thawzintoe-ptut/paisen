package com.ptut.expense.ui.play

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.meterShare
import com.ptut.expense.design.orStill
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutMotion

/** A filled share of a pill track (#84). The fill moves to a new share; it never fades. */
@Composable
fun PlayMeter(
    fraction: Float,
    label: String,
    modifier: Modifier = Modifier,
    track: Color = MaterialTheme.ptut.barTrack,
    fill: Color = MaterialTheme.colorScheme.primary,
) {
    val motion = MaterialTheme.ptutMotion
    val share by animateFloatAsState(
        targetValue = meterShare(fraction),
        animationSpec = tween(durationMillis = motion.meterFill.orStill(), easing = motion.standard),
        label = "play meter",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(PlaySizes.meter)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(meterShare(fraction), 0f..1f)
            }
            .clip(CircleShape)
            .background(track),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(share).clip(CircleShape).background(fill))
    }
}
