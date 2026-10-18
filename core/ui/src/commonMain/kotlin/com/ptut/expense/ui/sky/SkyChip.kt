package com.ptut.expense.ui.sky

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.orStill
import com.ptut.expense.design.ptutMotion
import com.ptut.expense.locale.LocalCopy

/**
 * A category chip (p10, p13): white with a hairline, ink when chosen, no glyph. The pill is painted
 * at [height] inside a 44 tap area, so a wrapped row of chips needs no gap of its own.
 */
@Composable
fun SkyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = SkySizes.chip,
) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.tertiaryContainer,
        animationSpec = chipFade(),
        label = "sky chip fill",
    )
    val ink by animateColorAsState(
        targetValue = if (selected) scheme.onPrimary else scheme.onSurface,
        animationSpec = chipFade(),
        label = "sky chip ink",
    )
    SkyChipTarget(selected = selected, onClick = onClick, modifier = modifier) { press ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .heightIn(min = height)
                .clip(CircleShape)
                .then(press)
                .background(fill)
                .border(SkySizes.chipBorder, if (selected) Color.Transparent else scheme.surfaceContainerHigh, CircleShape)
                .padding(horizontal = Spacing.s14),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
        }
    }
}

/**
 * A sub-category chip on the grey box under the categories (p18): white, sky when chosen. Sky on
 * white is 1.4:1, so the chosen chip also draws a tick and does not rest on colour alone.
 */
@Composable
fun SkySubChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.tertiaryContainer,
        animationSpec = chipFade(),
        label = "sky sub-chip fill",
    )
    val ink = if (selected) scheme.onPrimaryContainer else scheme.onSurface
    SkyChipTarget(selected = selected, onClick = onClick, modifier = modifier) { press ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = SkySizes.subChip)
                .clip(CircleShape)
                .then(press)
                .background(fill)
                .padding(horizontal = Spacing.s13),
        ) {
            if (selected) {
                Icon(
                    imageVector = PtutIcon.Check.image,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(SkySizes.subChipTick),
                )
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
        }
    }
}

/**
 * The grey box under the category chips (p18): its label, then one [SkySubChip] per option. A
 * screen draws it only once a parent with sub-categories is chosen, so the common path is unchanged.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SkySubChipBox(
    options: List<T>,
    name: (T) -> String,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String = LocalCopy.current.common.subCategory,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PtutRadii.r16)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.s12, vertical = Spacing.xs),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            options.forEach { option ->
                SkySubChip(label = name(option), selected = option == selected, onClick = { onSelect(option) })
            }
        }
    }
}

/**
 * The 44 tall tap area around a painted pill. The ripple is handed to the pill through [content]'s
 * modifier, so it takes the pill's shape rather than the taller tap area's.
 */
@Composable
private fun SkyChipTarget(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable (press: Modifier) -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val ripple = LocalIndication.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = Sizes.minTouchTarget)
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ),
    ) {
        content(Modifier.indication(interactions, ripple))
    }
}

@Composable
private fun <T> chipFade() = tween<T>(
    durationMillis = MaterialTheme.ptutMotion.chip.orStill(),
    easing = MaterialTheme.ptutMotion.standard,
)
