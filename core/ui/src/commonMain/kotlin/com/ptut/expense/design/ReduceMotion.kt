package com.ptut.expense.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the reader has asked the system to stop animating things.
 *
 * The motion page makes this a rule rather than a nicety: "No state lives in movement alone.
 * Every animated state has a static equivalent — text, colour, or icon. Reduced motion holds the
 * last frame and loses nothing." So every helper in [Motion] and every mark in [Marks] reads this
 * and jumps to its end state instead of playing — never to a *blank* state, which would be the
 * one thing the rule forbids.
 *
 * A `staticCompositionLocalOf` rather than a parameter, because a screen that had to be handed
 * this would be a screen that could forget to pass it on.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Read it the way the theme reads everything else. */
val ptutReduceMotion: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalReduceMotion.current

/**
 * The system flag, per platform. Android reads the animator duration scale that Developer options
 * and battery saver both zero; iOS reads `UIAccessibilityIsReduceMotionEnabled`.
 *
 * "Both honour the system reduce-motion flag without a separate build" — page 06.
 */
@Composable
expect fun systemReduceMotion(): Boolean
