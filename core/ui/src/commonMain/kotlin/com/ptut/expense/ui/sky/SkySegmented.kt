package com.ptut.expense.ui.sky

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.orStill
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutMotion

/**
 * Expense / Income (p10): a grey track, the chosen segment in ink, each segment as wide as its word.
 * Both are drawn inside 44 tall targets, so the artboard's 36 and 30 hold and grow with large text.
 */
@Composable
fun SkySegmentedToggle(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surface
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .selectableGroup()
            .drawBehind { drawPill(track, inset = (Sizes.minTouchTarget - SkySizes.segmentedTrack) / 2) }
            .padding(horizontal = SkySizes.segmentInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            Segment(label = option, selected = index == selected, onClick = { onSelect(index) })
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fade = tween<Color>(MaterialTheme.ptutMotion.chip.orStill(), easing = MaterialTheme.ptutMotion.standard)
    val fill by animateColorAsState(if (selected) scheme.primary else Color.Transparent, fade, label = "segment fill")
    val ink by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant, fade, label = "segment ink")
    val inset = (Sizes.minTouchTarget - SkySizes.segmentedTrack) / 2 + SkySizes.segmentInset
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxHeight()
            .heightIn(min = Sizes.minTouchTarget)
            // No ripple: the ink moving to the tapped segment is the answer, and a 44 tall ripple
            // would spill past the 36 track.
            .selectable(selected = selected, interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
            .drawBehind { drawPill(fill, inset = inset) }
            .padding(horizontal = Spacing.s13, vertical = inset),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
    }
}

/**
 * Home's Today / Week / Month (p05): equal columns on a hairline, the chosen word in ink with an ink
 * line under it, as wide as the word and 6 more a side.
 */
@Composable
fun SkyUnderlineTabs(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rule = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .selectableGroup()
            .drawBehind {
                val weight = SkySizes.hairline.toPx()
                drawRect(rule, topLeft = Offset(0f, size.height - weight), size = Size(size.width, weight))
            },
    ) {
        options.forEachIndexed { index, option ->
            val chosen = index == selected
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = SkySizes.tab)
                    .selectable(selected = chosen, role = Role.Tab, onClick = { onSelect(index) }),
            ) {
                // Width is the word's, so the line under it can fill this column and match.
                Column(modifier = Modifier.width(IntrinsicSize.Max).fillMaxHeight()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.s6),
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(SkySizes.tabUnderline)
                            .background(if (chosen) MaterialTheme.ptut.signature else Color.Transparent),
                    )
                }
            }
        }
    }
}

/** A full-width pill, [inset] in from the top and bottom of the node it is drawn behind. */
private fun DrawScope.drawPill(color: Color, inset: Dp) {
    val top = inset.toPx()
    val height = size.height - 2 * top
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, top),
        size = Size(size.width, height),
        cornerRadius = CornerRadius(height / 2),
    )
}
