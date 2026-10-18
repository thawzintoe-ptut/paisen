package com.ptut.expense.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Pure commonMain — no expect/actual, because dynamic color is off.
 *
 * Dynamic color needs an Android Context and only exists on Android 12+, so switching it on
 * would make the two platforms different products. It would also let the wallpaper move the
 * hues, which undermines the one signal that has to stay constant in a money app.
 */
@Composable
fun PtutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** After 18:00 with Mood by time of day on (#122). Nights stay dark and neutral. */
    evening: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(darkTheme, evening)
    val colorScheme = if (darkTheme) darkScheme(palette) else lightScheme(palette)

    CompositionLocalProvider(
        LocalPtutColors provides ptutColorsOf(palette),
        LocalPtutMotion provides PtutMotion.Default,
        // Read once, at the top, so no screen has to ask. Page 06: "Both honour the system
        // reduce-motion flag without a separate build."
        LocalReduceMotion provides systemReduceMotion(),
        LocalPtutType provides rememberPtutType(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ptutTypography(),
            shapes = PtutShapes,
            content = content,
        )
    }
}

/** Sky by day, SkyEvening after 18:00, SkyNight in dark mode (#143, #122). Pure, so a host test holds each case. */
fun paletteFor(darkTheme: Boolean, evening: Boolean = false): Palette = when {
    darkTheme -> SkyNight
    evening -> SkyEvening
    else -> Sky
}
