package com.ptut.expense.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutIcon
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyEmptyState
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkyTopBar

/**
 * FR-5 and US-8 in Sky (p23): what the importer did and what it could not do, one row each, with
 * the time at the right. A run that failed draws its glyph in red; a slip waiting on the reader
 * draws sky, the colour of To check. Every entry kind the data makes stays (#146, K25).
 *
 * #217: only the bar until the ledger answers, and a row about work left opens the place to do it.
 */
@Composable
fun ActivityView(
    uiState: ActivityUiState,
    onBack: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenSlip: (String) -> Unit,
    onOpenFolders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.slips
    val open: (ActivityTarget) -> Unit = { target ->
        when (target) {
            ActivityTarget.None -> Unit
            ActivityTarget.Review -> onOpenReview()
            is ActivityTarget.Slip -> onOpenSlip(target.id)
            ActivityTarget.PhotoFolders -> onOpenFolders()
        }
    }
    LazyColumn(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) {
        item { SkyTopBar(title = text.activityTitle, onBack = onBack) }
        if (uiState.loading) return@LazyColumn
        if (uiState.entries.isEmpty()) {
            item {
                SkyEmptyState(
                    headline = text.activityEmptyHeadline,
                    body = text.activityEmptyBody,
                    status = text.activityEmptyStatus,
                    statusIcon = PtutIcon.Schedule.image,
                    modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md),
                )
            }
        }
        items(uiState.entries, key = { it.id }) { entry -> EntryRow(entry, onOpen = { open(entry.target) }) }
        item { Box(Modifier.height(SkySizes.pillClearance)) }
    }
}

@Composable
private fun EntryRow(entry: ActivityEntryUiModel, onOpen: () -> Unit) {
    val text = LocalCopy.current.slips
    val (fill, ink) = entry.colours()
    Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SkySizes.transactionRow)
                .then(
                    if (entry.target == ActivityTarget.None) Modifier else Modifier.tappable(RectangleShape, Role.Button, onClick = onOpen),
                )
                .semantics(mergeDescendants = true) {
                    if (entry.tone == ActivityTone.NeedsYou) stateDescription = text.needsYou
                }
                .padding(vertical = Spacing.s12),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(SkySizes.rowCircle).clip(CircleShape).background(fill),
            ) {
                Icon(ptutIcon(entry.icon), contentDescription = null, tint = ink, modifier = Modifier.size(SkySizes.rowGlyph))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.ptutType.listHeadline, color = MaterialTheme.colorScheme.onSurface)
                Text(entry.body, style = MaterialTheme.ptutType.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = entry.time,
                style = MaterialTheme.ptutType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Top),
            )
        }
        SkyHairline()
    }
}

/** Ink for work done, ruby for a slip waiting, red for a run that failed, grey for a report. */
@Composable
private fun ActivityEntryUiModel.colours(): Pair<Color, Color> = when {
    tone == ActivityTone.Imported -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    icon == FAILED_ICON -> MaterialTheme.ptut.warnBg to MaterialTheme.ptut.warnInk
    icon == WAITING_ICON -> MaterialTheme.ptut.signature to MaterialTheme.ptut.onSignature
    else -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
}

/** The glyph names [ActivityViewModel] writes for a failed run and for a slip in To check. */
private const val FAILED_ICON = "error"
private const val WAITING_ICON = "inbox"
