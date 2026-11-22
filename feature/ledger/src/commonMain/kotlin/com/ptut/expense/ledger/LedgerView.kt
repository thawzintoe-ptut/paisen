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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
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
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.categoryAndProvenance
import com.ptut.expense.ui.sky.SkyBarIconAction
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyChip
import com.ptut.expense.ui.sky.SkyEmptyState
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkySearchField
import com.ptut.expense.ui.sky.SkyTextAction
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
    // Filled as the list emits its day headers, so a month jump finds its day by key.
    val dayIndex = remember { mutableMapOf<String, Int>() }
    val current by remember(state) {
        derivedStateOf {
            val under = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key != BEHIND_KEY && it.offset + it.size > lineHeight }
            state.monthOf(under?.key)
        }
    }
    val railWidth = with(density) { railWidthPx.toDp() } + Spacing.xxs
    val rowEnd = if (state.hasMonthIndex && railWidthPx > 0) maxOf(Spacing.md, railWidth) else Spacing.md

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            val list = CountedItems(this)
            list.item {
                // Sky draws no top-bar action here: the sliders beside search open the filters, and
                // Categories is reached from Settings (#128).
                SkyTopBar(title = state.scope?.title ?: text.title, onBack = onBack)
            }
            state.scope?.let { scope ->
                list.item {
                    Text(
                        text = scope.days,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(bottom = Spacing.xs),
                    )
                }
            }
            list.item {
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
                list.stickyHeader(key = BEHIND_KEY) {
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
                list.item {
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

            dayIndex.clear()
            state.days.forEach { day ->
                dayIndex[day.key] = list.size
                list.item(key = day.key) { DayHeader(day, end = rowEnd) }
                list.items(day.rows, key = { it.id }) { row ->
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
            list.item { Box(Modifier.height(SkySizes.pillClearance)) }
        }

        if (state.hasMonthIndex) {
            MonthIndex(
                months = state.months,
                current = current,
                onJump = { month ->
                    // Pulled down by the pinned line, so the month's first day header shows below it.
                    dayIndex[month.dayKey]?.let { index -> scroller.launch { listState.scrollToItem(index, -lineHeight) } }
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

/**
 * FR-21's filters (#146, K6), behind the sliders button on a sheet. Three chips first; "More
 * filters" reveals the rest, and opens already revealed while one of them is on.
 */
@Composable
private fun FilterSheet(chosen: LedgerFilter, onFilter: (LedgerFilter) -> Unit, onDismiss: () -> Unit) {
    val text = LocalCopy.current.history
    var more by rememberSaveable { mutableStateOf(chosen.opensMoreFilters()) }
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = text.show,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.s12),
        )
        FilterChips(PrimaryLedgerFilters, chosen, onFilter)
        if (more) {
            FilterChips(MoreLedgerFilters, chosen, onFilter, modifier = Modifier.padding(top = Spacing.xs))
        } else {
            SkyTextAction(label = text.moreFilters, onClick = { more = true }, enabled = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChips(filters: List<LedgerFilter>, chosen: LedgerFilter, onFilter: (LedgerFilter) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        filters.forEach { filter ->
            SkyChip(label = filter.label, selected = filter == chosen, onClick = { onFilter(filter) })
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
                // #98: a sealed month carries a small tick; its rows stay editable.
                if (month.sealed) Icon(PtutIcon.Check.image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Sizes.statusGlyph))
            }
        }
    }
}

private const val BEHIND_KEY = "behind"

/** Emits into the list and counts what it emitted, so an item's index is known where it is added. */
private class CountedItems(private val scope: LazyListScope) {
    var size = 0
        private set

    fun item(key: Any? = null, content: @Composable LazyItemScope.() -> Unit) {
        scope.item(key = key, content = content)
        size++
    }

    fun stickyHeader(key: Any, content: @Composable LazyItemScope.() -> Unit) {
        scope.stickyHeader(key = key) { content() }
        size++
    }

    fun <T : Any> items(items: List<T>, key: (T) -> Any, content: @Composable LazyItemScope.(T) -> Unit) {
        scope.items(items, key = key, itemContent = content)
        size += items.size
    }
}

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
