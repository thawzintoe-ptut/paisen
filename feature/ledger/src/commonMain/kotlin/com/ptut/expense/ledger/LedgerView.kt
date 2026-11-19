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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.launch

/**
 * FR-21 in Sky (p15): the whole table, grouped by Bangkok day, newest first, under a search pill
 * and the sliders button. It is pushed from Home or Reports, so it draws a back arrow (#217).
 * Opened from Reports, it holds that bar's days or that category's rows and says which (#220).
 *
 * Kept beyond the artboard (#146): the five filters, now on a sheet behind the sliders (K6), the row's
 * category and filer, "Groceries · auto" (K7), the income sign and colour, the filtered empty
 * states with Clear filters (K9), and the month in older day headers.
 *
 * Past one month (#70), a month index on the right edge jumps to a month's first day, and a line
 * pinned at the top counts the rows and months above.
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
    val listState = rememberLazyListState()
    val scroller = rememberCoroutineScope()
    val density = LocalDensity.current
    var railWidthPx by remember { mutableIntStateOf(0) }
    var lineHeight by remember { mutableIntStateOf(0) }
    // The items above the first day header: top bar, scope line, search row, and the pinned line.
    val leading = 2 + (if (state.scope != null) 1 else 0) + (if (state.hasMonthIndex) 1 else 0) + (if (state.isEmpty) 1 else 0)
    val current by remember(state, leading) {
        derivedStateOf {
            val under = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key != BEHIND_KEY && it.offset + it.size > lineHeight }
            state.monthAt((under?.index ?: 0) - leading)
        }
    }
    val railWidth = with(density) { railWidthPx.toDp() } + Spacing.xxs
    val rowEnd = if (state.hasMonthIndex && railWidthPx > 0) maxOf(Spacing.md, railWidth) else Spacing.md

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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

            if (state.hasMonthIndex) {
                stickyHeader(key = BEHIND_KEY) {
                    Text(
                        text = current?.behind.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                            .onSizeChanged { lineHeight = it.height },
                    )
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
                item(key = day.date) { DayHeader(day, end = rowEnd) }
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
                        modifier = Modifier.padding(start = Spacing.md, end = rowEnd),
                    )
                }
            }
            item { Box(Modifier.height(SkySizes.pillClearance)) }
        }

        if (state.hasMonthIndex) {
            MonthIndex(
                months = state.months,
                current = current,
                onJump = { month ->
                    // Pulled down by the pinned line, so the month's first day header shows below it.
                    scroller.launch { listState.scrollToItem(leading + month.firstItem, -lineHeight) }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = SkySizes.topBar + SkySizes.search + Spacing.sm, bottom = SkySizes.pillClearance, end = Spacing.xxs)
                    .onSizeChanged { railWidthPx = it.width },
            )
        }
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

/**
 * #70: each month is its own 44dp button, so TalkBack and VoiceOver reach it by name. It scrolls
 * when more months than fit are listed.
 */
@Composable
private fun MonthIndex(
    months: List<LedgerMonthUiModel>,
    current: LedgerMonthUiModel?,
    onJump: (LedgerMonthUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Sizes.minTouchTarget / 2)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        months.forEach { month ->
            val chosen = month == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .heightIn(min = Sizes.minTouchTarget)
                    .widthIn(min = Sizes.minTouchTarget)
                    .tappable(shape, Role.Button) { onJump(month) }
                    .clearAndSetSemantics { contentDescription = month.spoken }
                    .padding(horizontal = Spacing.xxs),
            ) {
                month.year?.let { year ->
                    Text(year, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
                Text(
                    text = month.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private const val BEHIND_KEY = "behind"

@Composable
private fun DayHeader(day: LedgerDayUiModel, end: Dp) {
    Column(modifier = Modifier.padding(start = Spacing.md, end = end).padding(top = Spacing.sm)) {
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
