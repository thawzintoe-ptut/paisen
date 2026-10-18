package com.ptut.expense.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * The Material 3 half of a [Palette]. [Sky] is the light theme and [SkyNight] its dark pair. Both
 * map the same way, so a theme swap is a change of palette, not of this file.
 *
 * `primaryContainer` carries `accent`, sky: a single-use colour, pinned to a role that Material
 * only uses on request so it does not spread.
 *
 * The four error roles resolve to the `warn` family, so stock Material red never reaches the
 * banner FR-5 and US-8 rest on.
 */
fun lightScheme(palette: Palette): ColorScheme = lightColorScheme(
    primary = palette.primary,
    onPrimary = palette.onPrimary,
    primaryContainer = palette.accent,
    onPrimaryContainer = palette.accentOn,
    secondaryContainer = palette.containerHigh,
    onSecondaryContainer = palette.onSurface,
    tertiaryContainer = palette.container,
    onTertiaryContainer = palette.onSurface,
    background = palette.surface,
    onBackground = palette.onSurface,
    surface = palette.surface,
    onSurface = palette.onSurface,
    onSurfaceVariant = palette.onVar,
    surfaceContainerLowest = palette.surface,
    surfaceContainerLow = palette.sheetBg,
    surfaceContainer = palette.navBg,
    surfaceContainerHigh = palette.containerHigh,
    surfaceContainerHighest = palette.containerHigh,
    error = palette.warn,
    onError = palette.warnOn,
    errorContainer = palette.warnBg,
    onErrorContainer = palette.onSurface,
    outline = palette.onVar,
    outlineVariant = palette.outline,
    inverseSurface = palette.toastBg,
    inverseOnSurface = palette.toastOn,
)

fun darkScheme(palette: Palette): ColorScheme = darkColorScheme(
    primary = palette.primary,
    onPrimary = palette.onPrimary,
    primaryContainer = palette.accent,
    onPrimaryContainer = palette.accentOn,
    secondaryContainer = palette.containerHigh,
    onSecondaryContainer = palette.onSurface,
    tertiaryContainer = palette.container,
    onTertiaryContainer = palette.onSurface,
    background = palette.surface,
    onBackground = palette.onSurface,
    surface = palette.surface,
    onSurface = palette.onSurface,
    onSurfaceVariant = palette.onVar,
    surfaceContainerLowest = palette.surface,
    surfaceContainerLow = palette.sheetBg,
    surfaceContainer = palette.navBg,
    surfaceContainerHigh = palette.containerHigh,
    surfaceContainerHighest = palette.containerHigh,
    error = palette.warn,
    onError = palette.warnOn,
    errorContainer = palette.warnBg,
    onErrorContainer = palette.onSurface,
    outline = palette.onVar,
    outlineVariant = palette.outline,
    inverseSurface = palette.toastBg,
    inverseOnSurface = palette.toastOn,
)
