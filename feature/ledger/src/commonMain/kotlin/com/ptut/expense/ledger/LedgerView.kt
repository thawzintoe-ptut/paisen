package com.ptut.expense.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.tappable
import com.ptut.expense.domain.LedgerFilter
import com.ptut.expense.domain.categoryAndProvenance
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyBarIconAction
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyChip
import com.ptut.expense.ui.sky.SkyEmptyState
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkySearchField
import com.ptut.expense.ui.sky.SkyTopBar
import com.ptut.expense.ui.sky.SkyTransactionRow

/**
 * FR-21 in Sky (p15): the whole table, grouped by Bangkok day, newest first, under a search pill
 * and the sliders button. It is pushed from Home or Reports, so it draws a back arrow (#217).
 * Opened from Reports, it holds that bar's days or that category's rows and says which (#220).
 *
 * Kept beyond the artboard (#146): the five filters, now on a sheet behind the sliders (K6), the row's
 * category and filer, "Groceries · auto" (K7), the income sign and colour, the filtered empty
 * states with Clear filters (K9), and the month in older day headers.
 */
@Composable
fun LedgerView(
    uiState: LedgerUiState,
    onSearch: (String) -> Unit,
    onFilter: (LedgerFilter) -> Unit,
    onOpenTransaction: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) {
        when (uiState) {
            LedgerUiState.Loading -> Unit
            is LedgerUiState.Success -> LoadedLedger(uiState, onSearch, onFilter, onOpenTransaction, onBack)
        }
    }
}

@Composable
private fun LoadedLedger(
    state: LedgerUiState.Success,
    onSearch: (String) -> Unit,
    onFilter: (LedgerFilter) -> Unit,
    onOpenTransaction: (String) -> Unit,
    onBack: () -> Unit,
) {
    var filtering by rememberSaveable { mutableStateOf(false) }
    val text = LocalCopy.current.history
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Sky draws no top-bar action here: the sliders beside search open the filters, and
            // Categories is reached from Settings (#128).
            SkyTopBar(title = state.scope?.title ?: text.title, onBack = onBack)
        }
        state.scope?.let { scope ->
            item {
                Text(
                    text = scope.days,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(bottom = Spacing.xs),
                )
            }
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.sm).padding(bottom = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkySearchField(
                    query = state.query,
                    onSearch = onSearch,
                    placeholder = text.search,
                    modifier = Modifier.weight(1f),
                )
                FiltersButton(filter = state.filter, onClick = { filtering = true })
            }
        }

        if (state.isEmpty) {
            item {
                SkyEmptyState(
                    headline = if (state.ledgerIsEmpty) text.emptyHeadline else text.noMatchHeadline,
                    body = if (state.ledgerIsEmpty) text.emptyBody else text.noMatchBody,
                    status = if (state.ledgerIsEmpty) text.emptyStatus else text.hiddenByFilter(state.hiddenByFilter),
                    statusIcon = if (state.ledgerIsEmpty) PtutIcon.Sync.image else PtutIcon.FilterAltOff.image,
                    // Only where there is something to clear: on an empty ledger it would do nothing.
                    cta = text.clearFilters.takeUnless { state.ledgerIsEmpty },
                    onCta = {
                        onSearch("")
                        onFilter(LedgerFilter.All)
                    }.takeUnless { state.ledgerIsEmpty },
                    modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.sm),
                )
            }
        }

        state.days.forEach { day ->
            item(key = day.date) { DayHeader(day) }
            items(day.rows, key = { it.id }) { row ->
                SkyTransactionRow(
                    merchant = row.merchant,
                    meta = row.parts.categoryAndProvenance(),
                    amount = row.amount,
                    icon = row.icon,
                    direction = row.direction,
                    time = row.parts.time,
                    categoryColor = row.color,
                    onClick = { onOpenTransaction(row.id) },
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }
        }
        item { Box(Modifier.height(SkySizes.pillClearance)) }
    }

    if (filtering) {
        FilterSheet(chosen = state.filter, onFilter = {
            onFilter(it)
            filtering = false
        }, onDismiss = { filtering = false })
    }
}

/** The sliders button (p15), with a dot while a filter other than All is on, so a short list explains itself. */
@Composable
private fun FiltersButton(filter: LedgerFilter, onClick: () -> Unit) {
    val text = LocalCopy.current.history
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SkySizes.search)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .semantics {
                contentDescription = text.filters
                stateDescription = filter.label
            }
            .tappable(CircleShape, Role.Button, onClick = onClick),
    ) {
        Icon(PtutIcon.RuleSettings.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.searchGlyph))
        if (filter != LedgerFilter.All) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.s10)
                    .size(Spacing.xs)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** FR-21's five filters (#146, K6), behind the sliders button on a sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(chosen: LedgerFilter, onFilter: (LedgerFilter) -> Unit, onDismiss: () -> Unit) {
    val text = LocalCopy.current.history
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = text.show,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.s12),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            LedgerFilter.entries.forEach { filter ->
                SkyChip(label = filter.label, selected = filter == chosen, onClick = { onFilter(filter) })
            }
        }
    }
}

@Composable
private fun DayHeader(day: LedgerDayUiModel) {
    Column(modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(day.date, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(day.total, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SkyHairline()
    }
}
