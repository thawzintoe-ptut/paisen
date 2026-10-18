package com.ptut.expense.design

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android has no single "reduce motion" switch, so it is read off the scale the platform actually
 * animates by. Developer options, battery saver and the accessibility "remove animations" setting
 * all zero it, and `ValueAnimator.areAnimatorsEnabled()` is the same check one layer up.
 *
 * `TRANSITION_ANIMATION_SCALE` is read too: it is the one the system's own screen transitions
 * honour, and a reader who turned that off asked for exactly what page 02 draws to stop.
 */
@Composable
actual fun systemReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        val animator = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val transition = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        animator == 0f || transition == 0f
    }
}
