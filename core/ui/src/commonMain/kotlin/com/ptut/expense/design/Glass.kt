package com.ptut.expense.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/**
 * Whether surfaces are drawn as glass (#123): the reader's Transparency switch, and a device that
 * can blur. A `staticCompositionLocalOf` for the reason [LocalReduceMotion] is one: a screen handed
 * it as a parameter is a screen that could forget to pass it on.
 */
val LocalGlassOn = staticCompositionLocalOf { false }

/** The shell's backdrop, for surfaces that float over every screen (the toast). Null outside it. */
val LocalShellBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/**
 * What glass blurs: one recorded copy of the content under it, and where that content sits. The
 * content draws through [glassSource]; a [GlassSurface] above it draws the copy again, blurred.
 */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer, val enabled: Boolean) {
    internal var origin by mutableStateOf(Offset.Zero)
}

/** A backdrop that is on while [LocalGlassOn] is; off, every surface over it is solid. */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    val enabled = LocalGlassOn.current
    return remember(layer, enabled) { GlassBackdrop(layer, enabled) }
}

/** Draws as usual, and records what it drew into [backdrop] for glass floating above it. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier =
    if (!backdrop.enabled) {
        this
    } else {
        onGloballyPositioned { backdrop.origin = it.positionInRoot() }
            .drawWithContent {
                backdrop.layer.record { this@drawWithContent.drawContent() }
                drawLayer(backdrop.layer)
            }
    }

/**
 * A floating surface in iOS glass: what scrolls under it blurred and its colour lifted, a [tint]
 * of its own [fill] over that, light catching the top, and a rim. Solid [fill] when [backdrop] is
 * null or off. [tint] is per surface: the lowest that keeps that surface's own ink readable.
 */
@Composable
fun GlassSurface(
    backdrop: GlassBackdrop?,
    shape: Shape,
    fill: Color,
    tint: Float,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = backdrop?.takeIf { it.enabled }
    val ground = MaterialTheme.colorScheme.background
    val light = MaterialTheme.ptut.glassHighlight
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        contentAlignment = contentAlignment,
        content = content,
        modifier = modifier
            .clip(shape)
            .then(
                if (glass == null) {
                    Modifier.background(fill, shape)
                } else {
                    Modifier
                        .onGloballyPositioned { origin = it.positionInRoot() }
                        .drawWithCache {
                            val blurred = obtainGraphicsLayer().apply {
                                val radius = SkySizes.glassBlur.toPx()
                                renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                                // iOS materials lift the colour of what they blur, so the glass never turns grey.
                                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(GLASS_SATURATION) })
                            }
                            val outline = shape.createOutline(size, layoutDirection, this)
                            // Twice the hairline, centred on the edge: the clip keeps the inner half.
                            val rimStroke = Stroke(SkySizes.hairline.toPx() * 2)
                            val rim = Brush.verticalGradient(listOf(light.copy(alpha = RIM_TOP), light.copy(alpha = RIM_BOTTOM)))
                            val sheen = Brush.verticalGradient(0f to light.copy(alpha = GLASS_SHEEN), SHEEN_DEPTH to Color.Transparent)
                            onDrawBehind {
                                // The page first, so content that leaves pixels clear shows the page, not a hole.
                                drawRect(ground)
                                blurred.record {
                                    val shift = glass.origin - origin
                                    translate(shift.x, shift.y) { drawLayer(glass.layer) }
                                }
                                drawLayer(blurred)
                                drawRect(fill.copy(alpha = tint))
                                drawRect(sheen)
                                drawOutline(outline, rim, style = rimStroke)
                            }
                        }
                },
            ),
    )
}

/** iOS-style vibrancy: how much the blurred page's colour is lifted under the glass. */
private const val GLASS_SATURATION = 1.8f

/** The white catching the top of the glass, and how far down it fades. At 0.08 night pill glyphs keep 3.17:1. */
private const val GLASS_SHEEN = 0.08f
private const val SHEEN_DEPTH = 0.5f

/** The rim: bright where light hits the top edge, faint along the bottom. */
private const val RIM_TOP = 0.45f
private const val RIM_BOTTOM = 0.1f
