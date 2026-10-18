package com.ptut.expense.ui.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.tappable

/** Ink fills the one commitment on a screen; red means delete and nothing else (p02). */
enum class SkyButtonKind { Ink, Outline, DangerOutline, Danger }

/**
 * Sky's pill. Copy stays sentence case and the pill draws it in capitals, so a screen reader hears
 * "Not mine" and a test finds the button by that name.
 */
@Composable
fun SkyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: SkyButtonKind = SkyButtonKind.Ink,
    enabled: Boolean = true,
) {
    val colors = skyButtonColors(kind, enabled)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = SkySizes.button)
            .semantics { contentDescription = label }
            .tappable(CircleShape, Role.Button, enabled, onClick)
            .background(colors.fill)
            .then(
                if (colors.edge == null) {
                    Modifier
                } else {
                    Modifier.border(SkySizes.buttonBorder, colors.edge, CircleShape)
                },
            )
            .padding(horizontal = Spacing.md),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = SkyLabelTracking),
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * The top bar's word action, "Save". Inert and grey until the screen holds a change to keep. [ink]
 * is for a surface with its own colours, such as the toast's Undo (#179).
 */
@Composable
fun SkyTextAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    ink: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sizeIn(minWidth = Sizes.minTouchTarget, minHeight = Sizes.minTouchTarget)
            .tappable(PtutRadii.r12, Role.Button, enabled, onClick)
            .padding(horizontal = Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) ink else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Measured off CONFIRM and NEW CATEGORY: about 0.05em between capitals at 14sp. */
internal val SkyLabelTracking = 0.05.em

private class SkyButtonColors(val fill: Color, val ink: Color, val edge: Color?)

@Composable
private fun skyButtonColors(kind: SkyButtonKind, enabled: Boolean): SkyButtonColors {
    val scheme = MaterialTheme.colorScheme
    val ptut = MaterialTheme.ptut
    val filled = kind == SkyButtonKind.Ink || kind == SkyButtonKind.Danger
    return when {
        !enabled && filled -> SkyButtonColors(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, null)

        !enabled -> SkyButtonColors(Color.Transparent, scheme.onSurfaceVariant, scheme.outlineVariant)

        kind == SkyButtonKind.Ink -> SkyButtonColors(scheme.primary, scheme.onPrimary, null)

        kind == SkyButtonKind.Outline -> SkyButtonColors(Color.Transparent, scheme.onSurface, scheme.onSurface)

        // warnInk, not warn: it is the red that stays readable as a foreground, by day and by night.
        kind == SkyButtonKind.DangerOutline -> SkyButtonColors(Color.Transparent, ptut.warnInk, ptut.warnInk)

        else -> SkyButtonColors(ptut.warn, ptut.warnOn, null)
    }
}
