package com.ptut.expense.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The handoff tokens Material 3 has no role for, so screens can read them without reaching
 * for a literal. Values come from the active [Palette]; see [ptutColorsOf].
 *
 * The ones that carry meaning rather than decoration:
 * - [warn] is delete and the stale-import notice, never a general accent. It fills; [warnInk]
 *   is the same warning as a foreground, dark enough to be read on its own tint.
 * - [ok] is income and success.
 * - [accent] is sky: the chart field and the soft tiles. It is also `primaryContainer`, so most
 *   callers get it from the colour scheme.
 * - [signature] is ruby: spending, a Net below zero, what you owe, slips waiting, Add, the chosen
 *   period's line and the brand mark's dot. It fills; [signatureInk] carries it as text. Never a warning.
 * - [infoBg] is something to know before acting, never a warning. It is pale sky, because red
 *   means delete and stale data only.
 * - [live] is the importer running, apart from [ok].
 */
@Immutable
data class PtutColors(
    val accent: Color,
    val accentOn: Color,
    val signature: Color,
    val onSignature: Color,
    val signatureInk: Color,
    val heroBg: Color,
    val heroOn: Color,
    val heroInner: Color,
    val navBg: Color,
    val pillShadow: Color,
    val glassHighlight: Color,
    val barTrack: Color,
    val stripe: Color,
    val slipCap: Color,
    val warn: Color,
    val warnInk: Color,
    val warnOn: Color,
    val warnBg: Color,
    val ok: Color,
    val toastBg: Color,
    val toastOn: Color,
    val live: Color,
    val toastCheck: Color,
    val infoBg: Color,
    val infoInk: Color,
    val categoryTints: CategoryTints,
)

fun ptutColorsOf(palette: Palette): PtutColors = PtutColors(
    accent = palette.accent,
    accentOn = palette.accentOn,
    signature = palette.signature,
    onSignature = palette.onSignature,
    signatureInk = palette.signatureInk,
    heroBg = palette.heroBg,
    heroOn = palette.heroOn,
    heroInner = palette.heroInner,
    navBg = palette.navBg,
    pillShadow = palette.pillShadow,
    glassHighlight = palette.glassHighlight,
    barTrack = palette.barTrack,
    stripe = palette.stripe,
    slipCap = palette.slipCap,
    warn = palette.warn,
    warnInk = palette.warnInk,
    warnOn = palette.warnOn,
    warnBg = palette.warnBg,
    ok = palette.ok,
    toastBg = palette.toastBg,
    toastOn = palette.toastOn,
    live = palette.live,
    toastCheck = palette.toastCheck,
    infoBg = palette.infoBg,
    infoInk = palette.infoInk,
    categoryTints = palette.categoryTints,
)

val LocalPtutColors = staticCompositionLocalOf { ptutColorsOf(Sky) }

val MaterialTheme.ptut: PtutColors
    @Composable
    @ReadOnlyComposable
    get() = LocalPtutColors.current
