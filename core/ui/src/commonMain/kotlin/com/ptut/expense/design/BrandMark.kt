package com.ptut.expense.design

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's own mark, the shape the launcher draws: an ink rounded square holding a lowercase p made
 * from a sky rounded-square ring, with a ruby dot in its counter (icon C, the ring p). The launcher
 * icons still draw that dot white.
 *
 * Not a [PtutIcon]: this is artwork, not a glyph. It keeps its colours in both themes, as a
 * launcher icon does, so it is drawn with `Image`, not tinted with `Icon`.
 *
 * Drawn in a 128-unit box, the 1024 iOS icon divided by 8, so the launcher layers share its numbers.
 * The square keeps p23's corner (9 of 30).
 */
val PtutBrandMark: ImageVector = ImageVector.Builder(
    name = "ptut-brand-mark",
    defaultWidth = 30.dp,
    defaultHeight = 30.dp,
    viewportWidth = 128f,
    viewportHeight = 128f,
).apply {
    addPath(
        pathData = addPathNodes(
            "M 38.4,0 H 89.6 A 38.4,38.4 0 0 1 128,38.4 V 89.6 A 38.4,38.4 0 0 1 89.6,128 H 38.4 " +
                "A 38.4,38.4 0 0 1 0,89.6 V 38.4 A 38.4,38.4 0 0 1 38.4,0 Z",
        ),
        fill = SolidColor(Sky.primary),
    )
    addPath(
        pathData = addPathNodes("M 57,32 H 71 A 14,14 0 0 1 85,46 V 60 A 14,14 0 0 1 71,74 H 57 A 14,14 0 0 1 43,60 V 46 A 14,14 0 0 1 57,32 Z"),
        fill = null,
        stroke = SolidColor(Sky.accent),
        strokeLineWidth = 7f,
    )
    addPath(
        pathData = addPathNodes("M 43,46 V 96"),
        fill = null,
        stroke = SolidColor(Sky.accent),
        strokeLineWidth = 7f,
        strokeLineCap = StrokeCap.Round,
    )
    addPath(
        pathData = addPathNodes("M 60.5,53 A 3.5,3.5 0 1 1 67.5,53 A 3.5,3.5 0 1 1 60.5,53 Z"),
        fill = SolidColor(Sky.signature),
    )
}.build()
