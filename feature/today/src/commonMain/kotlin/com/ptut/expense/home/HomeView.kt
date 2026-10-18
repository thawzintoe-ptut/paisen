package com.ptut.expense.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.PulseDot
import com.ptut.expense.design.QuietDayArt
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutIcon
import com.ptut.expense.domain.ImportFix
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.categoryAndProvenance
import com.ptut.expense.domain.format
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.formatParts
import com.ptut.expense.domain.spoken
import com.ptut.expense.locale.CommonCopy
import com.ptut.expense.locale.HomeCopy
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyEmptyState
import com.ptut.expense.ui.sky.SkyMoodCard
import com.ptut.expense.ui.sky.SkyPageSheet
import com.ptut.expense.ui.sky.SkyRollingAmount
import com.ptut.expense.ui.sky.SkySectionHeader
import com.ptut.expense.ui.sky.SkyStatCard
import com.ptut.expense.ui.sky.SkyTextAction
import com.ptut.expense.ui.sky.SkyTransactionRow
import com.ptut.expense.ui.sky.SkyUnderlineTabs
import com.ptut.expense.ui.sky.SkyWarningBanner

/**
 * Home in Sky (p05, p06, p24): Today / Week / Month over the amount and the period's top three
 * categories on white, the review card on the grey page, then the period's rows on a white sheet.
 *
 * It keeps what #146 keeps and the artboards leave out: the freshness line with its pulse, the
 * quiet stale state and "No slips imported yet", Income and Net, Saved today, the span in the
 * amount's label and one spoken label for the amount.
 */
@Composable
fun HomeView(
    uiState: HomeUiState,
    onChoosePeriod: (HomePeriod) -> Unit,
    onOpenReview: () -> Unit,
    onOpenLedger: () -> Unit,
    onAddByHand: () -> Unit,
    onFixImport: (ImportFix) -> Unit,
    onOpenTransaction: (String) -> Unit,
    /** A mood card's day receipt, by its ISO date (#120). */
    onOpenReceipt: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        when (uiState) {
            HomeUiState.Loading -> Unit

            is HomeUiState.Success -> LoadedHome(
                state = uiState,
                onChoosePeriod = onChoosePeriod,
                onOpenReview = onOpenReview,
                onOpenLedger = onOpenLedger,
                onAddByHand = onAddByHand,
                onFixImport = onFixImport,
                onOpenTransaction = onOpenTransaction,
                onOpenReceipt = onOpenReceipt,
            )
        }
    }
}

@Composable
private fun LoadedHome(
    state: HomeUiState.Success,
    onChoosePeriod: (HomePeriod) -> Unit,
    onOpenReview: () -> Unit,
    onOpenLedger: () -> Unit,
    onAddByHand: () -> Unit,
    onFixImport: (ImportFix) -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenReceipt: (String) -> Unit,
) {
    val text = LocalCopy.current.home
    val common = LocalCopy.current.common
    val empty = state.transactions.isEmpty()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(bottom = Spacing.md),
            ) {
                SkyUnderlineTabs(
                    options = HomePeriod.entries.map { it.label(text, common) },
                    selected = state.period.ordinal,
                    onSelect = { onChoosePeriod(HomePeriod.entries[it]) },
                )
                ImportStatus(
                    state = state,
                    onFixImport = onFixImport,
                    modifier = Modifier.padding(horizontal = Spacing.sm).padding(top = Spacing.sm),
                )
                // Below the freshness line, so a stale banner stays the louder of the two (#121).
                state.moodCard?.let { card ->
                    SkyMoodCard(
                        lead = card.lead,
                        line = card.line,
                        icon = ptutIcon(card.glyph),
                        onClick = { onOpenReceipt(card.receiptDate) },
                        modifier = Modifier.padding(horizontal = Spacing.sm).padding(top = Spacing.sm),
                    )
                }
                PeriodAmount(state = state, empty = empty, modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.sm))
                if (state.topCategories.isNotEmpty()) {
                    TopCategoryTiles(state.topCategories, Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md))
                }
                if (empty) {
                    SkyEmptyState(
                        headline = state.period.quiet(text),
                        body = text.emptyBody,
                        status = state.watchLine,
                        statusIcon = PtutIcon.Sync.image,
                        illustration = { QuietDayArt() },
                        waiting = state.isScanning,
                        cta = text.addCash,
                        onCta = onAddByHand,
                        modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md),
                    )
                    // The Purchases header is History's door, and an empty period draws none (#217).
                    SkyTextAction(
                        label = text.searchHistory,
                        onClick = onOpenLedger,
                        enabled = true,
                        modifier = Modifier.padding(horizontal = Spacing.sm).padding(top = Spacing.xs),
                    )
                }
            }
        }

        // p05's pair, Compared beside To check; either one alone takes the full width (#119).
        if (state.compared != null || state.review != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = Spacing.sm).padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
                ) {
                    state.compared?.let { compared ->
                        SkyStatCard(
                            title = text.compared,
                            line = compared.line,
                            icon = ptutIcon(compared.glyph),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    state.review?.let { review ->
                        SkyStatCard(
                            title = review.title,
                            line = review.detail,
                            icon = PtutIcon.Inbox.image,
                            onClick = onOpenReview,
                            filled = true,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }

        if (!empty || state.savedToday.isNotEmpty()) {
            item {
                SkyPageSheet(modifier = Modifier.padding(top = Spacing.sm)) {
                    if (!empty) {
                        SkySectionHeader(text = text.purchases, onClick = onOpenLedger)
                    }
                }
            }
            items(state.transactions, key = { it.id }) { row ->
                SheetRow { SkyRow(row = row, onOpenTransaction = onOpenTransaction) }
            }
            if (state.savedToday.isNotEmpty()) {
                item {
                    SheetRow {
                        Column {
                            SkySectionHeader(text = text.savedToday, modifier = Modifier.padding(top = Spacing.xs))
                            // Said out loud, or a save from another day looks like it never reached the total.
                            Text(
                                text = text.savedTodayNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(state.savedToday, key = { "saved-${it.id}" }) { row ->
                    // Its own day first, "Wed 12 Aug · Food & drink": it counts there, not today.
                    SheetRow { SkyRow(row = row, onOpenTransaction = onOpenTransaction, meta = row.meta) }
                }
                if (state.savedTodayMore > 0) {
                    item {
                        SheetRow {
                            SkyTextAction(label = text.moreInHistory(state.savedTodayMore), onClick = onOpenLedger, enabled = true)
                        }
                    }
                }
            }
            // The sheet runs on under the pill, so the last row comes to rest above it.
            item { SheetRow { Box(Modifier.heightIn(min = SkySizes.pillClearance)) } }
        } else {
            item { Box(Modifier.heightIn(min = SkySizes.pillClearance)) }
        }
    }
}

/** A row of the white sheet, continuing the [SkyPageSheet] the section header opened. */
@Composable
private fun SheetRow(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = Spacing.s20),
    ) { content() }
}

@Composable
private fun SkyRow(
    row: TransactionRowUiModel,
    onOpenTransaction: (String) -> Unit,
    meta: String = row.parts.categoryAndProvenance(),
) {
    SkyTransactionRow(
        merchant = row.merchant,
        meta = meta,
        amount = row.amount,
        icon = row.icon,
        direction = row.direction,
        time = row.parts.time,
        categoryColor = row.color,
        onClick = { onOpenTransaction(row.id) },
    )
}

/**
 * The freshness line above the amount (#146, K4), or the stale banner (p06) once the importer has
 * gone quiet. Under three days the banner has no action: C-5 makes a quiet stretch a healthy device.
 */
@Composable
private fun ImportStatus(
    state: HomeUiState.Success,
    onFixImport: (ImportFix) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.home
    val fix = state.importFix
    when (val status = state.importStatus) {
        is ImportStatusUiModel.Fresh -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // Page 05's pulse: it moves only while a scan is running.
            PulseDot(live = state.isScanning, size = Spacing.xs)
            Text(
                text = text.imported(status.since, state.summary.count, state.period.span(text)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ImportStatusUiModel.NeverRun -> SkyWarningBanner(
            title = text.noSlipsYet,
            body = text.totalsIncomplete,
            actionLabel = fix?.let { text.fix },
            onAction = fix?.let { { onFixImport(it) } },
            modifier = modifier,
        )

        is ImportStatusUiModel.Stale -> SkyWarningBanner(
            title = text.noSlipsFor(status.since),
            body = text.totalsIncomplete,
            actionLabel = fix?.let { text.fix },
            onAction = fix?.let { { onFixImport(it) } },
            modifier = modifier,
        )

        is ImportStatusUiModel.Broken -> SkyWarningBanner(
            title = text.noSlipsFor(status.since),
            body = text.totalsIncomplete,
            actionLabel = fix?.let { text.fix },
            onAction = fix?.let { { onFixImport(it) } },
            modifier = modifier,
        )

        // The reason stays in Activity: the journal keeps the language the scan ran in.
        ImportStatusUiModel.Failing -> SkyWarningBanner(
            title = text.scansFailing,
            body = null,
            actionLabel = fix?.let { text.fix },
            onAction = fix?.let { { onFixImport(it) } },
            modifier = modifier,
        )
    }
}

/**
 * "Spent today" and the amount at 52/300, the baht sign and the satang set smaller (p05). An empty
 * span draws ฿0.00 in grey (#147, R7), which with the status line below reads as "nothing yet".
 */
@Composable
private fun PeriodAmount(state: HomeUiState.Success, empty: Boolean, modifier: Modifier = Modifier) {
    val text = LocalCopy.current.home
    val common = LocalCopy.current.common
    val ink = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val parts = state.summary.expense.formatParts()
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "${state.heroLabel}, ${state.summary.expense.spoken()}"
        },
    ) {
        Text(state.heroLabel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Rolls to the next period's figure, and counts up from 0 the first time Today opens.
        SkyRollingAmount(
            symbol = "${parts.symbol} ",
            whole = parts.whole,
            fraction = parts.fraction,
            color = ink,
            fractionColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        // Income and Net for the span, shown nowhere else in Sky (#146, K1).
        if (!empty) {
            val net = state.summary.net
            val netInk = when {
                net < Satang.Zero -> MaterialTheme.ptut.signatureInk
                net > Satang.Zero -> MaterialTheme.ptut.ok
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = buildAnnotatedString {
                    append("${common.income} ${state.summary.income.format()} · ${text.net} ")
                    // Read as the rows are: no sign, no empty satang, ruby below zero and green above.
                    withStyle(SpanStyle(color = netInk)) { append(net.formatCompact()) }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
    }
}

/** The span's three biggest categories (p05): ink, then sky, then pale sky, biggest first. */
@Composable
private fun TopCategoryTiles(tiles: List<TopCategoryUiModel>, modifier: Modifier = Modifier) {
    val fills = listOf(
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary,
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.ptut.infoBg to MaterialTheme.ptut.infoInk,
    )
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        tiles.forEachIndexed { rank, tile ->
            val (fill, ink) = fills[rank.coerceAtMost(fills.lastIndex)]
            CategoryTile(tile = tile, fill = fill, ink = ink)
        }
    }
}

@Composable
private fun CategoryTile(tile: TopCategoryUiModel, fill: Color, ink: Color) {
    Row(
        modifier = Modifier
            .heightIn(min = SkySizes.chip)
            .clip(PtutRadii.r12)
            .background(fill)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.s14),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ptutIcon(tile.icon), contentDescription = null, tint = ink, modifier = Modifier.size(SkySizes.cardGlyph))
        Text(tile.name, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
    }
}

private fun HomePeriod.span(text: HomeCopy): String = when (this) {
    HomePeriod.Today -> text.spanToday
    HomePeriod.Week -> text.spanThisWeek
    HomePeriod.Month -> text.spanThisMonth
}

private fun HomePeriod.label(text: HomeCopy, common: CommonCopy): String = when (this) {
    HomePeriod.Today -> common.today
    HomePeriod.Week -> text.tabWeek
    HomePeriod.Month -> text.tabMonth
}

/** p24's headline, named for the span the reader chose. */
private fun HomePeriod.quiet(text: HomeCopy): String = when (this) {
    HomePeriod.Today -> text.quietDay
    HomePeriod.Week -> text.quietWeek
    HomePeriod.Month -> text.quietMonth
}
