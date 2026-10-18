package com.ptut.expense.ui.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut

/**
 * Sky's sheet (p10, p17): a white card 8 in from the screen's edges, round at every corner, with a
 * short grey handle. Apart from [SkyBottomSheet] so the debug kit can draw it without a modal.
 */
@Composable
fun SkySheetSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SkySizes.sheetCorner))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(start = Spacing.md, end = Spacing.md, top = Spacing.s14, bottom = Spacing.md),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(SkySizes.sheetHandleWidth, SkySizes.sheetHandleHeight)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        content()
    }
}

/**
 * [SkySheetSurface] in Material's modal sheet, which keeps the drag, the scrim and back to dismiss.
 * Its state stays inside, so callers need no opt-in to Material's experimental sheet API.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Opens part-way when the content is taller than half the screen (#130). Off by default, and
     * off for the add sheet too: part-way would hide its Save below the fold.
     */
    openPartway: Boolean = false,
    /**
     * #214: asked before a swipe, a scrim tap or back hides the sheet. False keeps it up, and the
     * caller asks instead, as the add sheet's "Discard changes?" does.
     */
    confirmDismiss: () -> Boolean = { true },
    content: @Composable ColumnScope.() -> Unit,
) {
    val confirm by rememberUpdatedState(confirmDismiss)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = !openPartway,
        confirmValueChange = { it != SheetValue.Hidden || confirm() },
    )
    ModalBottomSheet(
        // Back calls this even when the hide was refused, so a sheet still showing stays open.
        onDismissRequest = { if (!sheetState.isVisible) onDismissRequest() },
        modifier = modifier,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = Color.Transparent,
        tonalElevation = Spacing.none,
        // Drawn by the surface instead: Material's handle would sit outside the floating card.
        dragHandle = null,
    ) {
        SkySheetSurface(
            modifier = Modifier.padding(start = Spacing.xs, end = Spacing.xs, bottom = Spacing.xs),
            content = content,
        )
    }
}

/** How loud a [SkyStatusPill] is: grey for NOT COUNTED, ink for AMOUNT UNCLEAR so it gets noticed (K12). */
enum class SkyStatusPillTone { Quiet, Loud }

/** A small capitals pill on a review card (p08). Never a control, so it has no tap area. */
@Composable
fun SkyStatusPill(label: String, modifier: Modifier = Modifier, tone: SkyStatusPillTone = SkyStatusPillTone.Quiet) {
    val loud = tone == SkyStatusPillTone.Loud
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = label }
            .heightIn(min = SkySizes.statusPill)
            .clip(CircleShape)
            .background(if (loud) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.s10),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = SkyLabelTracking),
            color = if (loud) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** "✓ Amount read clearly" on confirm (p13): pale sky, the tick in ink. */
@Composable
fun SkyConfidenceBadge(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = SkySizes.confidenceBadge)
            .clip(CircleShape)
            .background(MaterialTheme.ptut.infoBg)
            .padding(horizontal = Spacing.s12),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = PtutIcon.Check.image,
            contentDescription = null,
            tint = MaterialTheme.ptut.infoInk,
            modifier = Modifier.size(SkySizes.badgeTick),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
