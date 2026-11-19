package com.ptut.expense.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.locale.SharedLogCopy
import com.ptut.expense.ui.sky.SkyChip
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkyLargeTitle
import com.ptut.expense.ui.sky.SkyTopBar

/**
 * #140 (p32). Named apart from v1's importer Activity in code; both read "Activity" on screen. The
 * art draws "You owe ฿640" in red: ink here, as decided on 13 Oct.
 */
@Composable
fun SharedActivityView(uiState: SharedActivityUiState, onBack: () -> Unit, onFilter: (ActivityFilter) -> Unit, modifier: Modifier = Modifier) {
    val text = LocalCopy.current.shared.log
    LazyColumn(modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) {
        item { SkyTopBar(title = "", onBack = onBack) }
        item { SkyLargeTitle(text = uiState.title) }
        item {
            FlowRow(
                modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                ActivityFilter.entries.forEach { filter ->
                    SkyChip(label = filter.label(text), selected = filter == uiState.filter, onClick = { onFilter(filter) })
                }
            }
        }
        if (uiState.days.isEmpty()) {
            item {
                Text(
                    text.empty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md),
                )
            }
        }
        activityDays(uiState.days, itemModifier = { Modifier })
        item { Spacer(Modifier.height(SkySizes.pillClearance)) }
    }
}

/**
 * The timeline by day, as this screen and the group's Activity tab (#64) list it. Days are keyed by
 * date: the label has no year, so it repeats a year apart.
 */
internal fun LazyListScope.activityDays(
    days: List<SharedActivityDay>,
    itemModifier: @Composable LazyItemScope.() -> Modifier,
    open: (SharedEntryUiModel) -> (() -> Unit)? = { null },
) {
    days.forEach { day ->
        item(key = "activity-day-${day.date}") { ActivityHeading(day.label, itemModifier()) }
        items(day.entries, key = { "entry-${it.id}" }) { entry -> Box(itemModifier()) { ActivityEntryRow(entry, onClick = open(entry)) } }
    }
}

@Composable
internal fun ActivityHeading(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md, bottom = Spacing.xxs),
    )
}

/** One change. On the group's Activity tab (#64) it can be new and open what it is about. */
@Composable
internal fun ActivityEntryRow(entry: SharedEntryUiModel, isNew: Boolean = false, onClick: (() -> Unit)? = null) {
    val changedTo = LocalCopy.current.shared.log.changedTo
    val (fill, ink) = when (entry.kind) {
        SharedEntryKind.Added -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        SharedEntryKind.Payment -> MaterialTheme.ptut.ok.copy(alpha = PAYMENT_TINT) to MaterialTheme.ptut.ok
        SharedEntryKind.Edited, SharedEntryKind.Removed -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
    }
    Column(Modifier.padding(horizontal = Spacing.md)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SkySizes.transactionRow)
                .then(if (onClick != null) Modifier.tappable(PtutRadii.r12, Role.Button, onClick = onClick) else Modifier)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = Spacing.s12),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
            verticalAlignment = Alignment.Top,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(SkySizes.rowCircle).clip(CircleShape).background(fill)) {
                Icon(PtutIcon.named(entry.icon).image, contentDescription = null, tint = ink, modifier = Modifier.size(SkySizes.rowGlyph))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isNew) NewDot()
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    entry.before?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(PtutIcon.ChevronRight.image, contentDescription = changedTo, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SkySizes.cardGlyph))
                    }
                    entry.after?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface) }
                    Text(entry.line, style = MaterialTheme.typography.bodyMedium, color = entry.tone.ink())
                }
            }
        }
        SkyHairline()
    }
}

private fun ActivityFilter.label(text: SharedLogCopy): String = when (this) {
    ActivityFilter.All -> text.filterAll
    ActivityFilter.Expenses -> text.filterExpenses
    ActivityFilter.Payments -> text.filterPayments
    ActivityFilter.Edits -> text.filterEdits
}

/** The payment tile's pale green (p32), faint enough that its green glyph reads on it. */
private const val PAYMENT_TINT = 0.12f
