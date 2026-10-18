package com.ptut.expense.ui.sky

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.confirmActionLabel

/**
 * The bar every pushed Sky screen carries (p13, p16, p18, p19): the way out on the left, one action
 * on the right, and the title centred between two sides kept as wide as the wider of them.
 */
@Composable
fun SkyTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** A form closes rather than going back: an arrow would promise a step back it does not make. */
    backIcon: PtutIcon = PtutIcon.ArrowBack,
    backLabel: String = LocalCopy.current.common.back,
    trailing: (@Composable () -> Unit)? = null,
) {
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.topBar)
            .padding(horizontal = Spacing.s6),
        content = {
            Box(Modifier.layoutId(LEAD)) {
                if (onBack != null) SkyBarIconAction(icon = backIcon, label = backLabel, onClick = onBack)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.layoutId(TITLE).semantics { heading() },
            )
            Box(Modifier.layoutId(TRAIL)) { trailing?.invoke() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val lead = measurables.first { it.layoutId == LEAD }.measure(loose)
        val trail = measurables.first { it.layoutId == TRAIL }.measure(loose)
        val side = maxOf(lead.width, trail.width)
        val titleSpace = (constraints.maxWidth - 2 * side).coerceAtLeast(0)
        val label = measurables.first { it.layoutId == TITLE }.measure(loose.copy(maxWidth = titleSpace))
        val height = maxOf(constraints.minHeight, lead.height, trail.height, label.height)
        layout(constraints.maxWidth, height) {
            lead.placeRelative(0, (height - lead.height) / 2)
            label.placeRelative((constraints.maxWidth - label.width) / 2, (height - label.height) / 2)
            trail.placeRelative(constraints.maxWidth - trail.width, (height - trail.height) / 2)
        }
    }
}

/** A glyph in a Sky bar: back, close, pencil, plus or ⋯, drawn at 24 in a 44 target and named for screen readers. */
@Composable
fun SkyBarIconAction(icon: PtutIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(Sizes.minTouchTarget)
            .tappable(CircleShape, Role.Button, onClick = onClick),
    ) {
        Icon(
            imageVector = icon.image,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(SkySizes.barGlyph),
        )
    }
}

/** The bar's confirm word, named as the platform names it: Save on Android, Done on iOS (#147, R11). */
@Composable
fun SkyConfirmAction(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    SkyTextAction(label = confirmActionLabel, onClick = onClick, enabled = enabled, modifier = modifier)

/** Settings' large left-aligned title (p21): a tab root has no bar to centre a title in (#144, N5). */
@Composable
fun SkyLargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.ptutType.screenTitle,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .semantics { heading() }
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
}

private const val LEAD = "lead"
private const val TITLE = "title"
private const val TRAIL = "trail"
