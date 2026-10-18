package com.ptut.expense.reports

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.categoryTint
import com.ptut.expense.design.orStill
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutIcon
import com.ptut.expense.design.ptutMotion
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable
import com.ptut.expense.domain.ReportPeriod
import com.ptut.expense.domain.TrendDirection
import com.ptut.expense.domain.format
import com.ptut.expense.domain.formatNet
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.locale.ReportsCopy
import com.ptut.expense.ui.sky.SkyPageSheet
import com.ptut.expense.ui.sky.SkySectionHeader
import com.ptut.expense.ui.sky.SkyTopBar

/**
 * Reports in Sky, "Spending" (p14, p15): a sky field holding the period tabs, the total and the
 * chart, and a white sheet ranking the categories under it.
 *
 * It keeps what the artboard leaves out (#146): Income and Net, the
 * movement against the period before, and each category's own movement. The empty period keeps
 * today's trigger, no rows at all (#147, R8), so an income-only week still shows its tiles.
 *
 * #220: the selected bar and each category row open those purchases in History, and the dates
 * the total covers sit under it.
 */
@Composable
fun ReportsView(
    uiState: ReportsUiState,
    onChoosePeriod: (ReportPeriod) -> Unit,
    onSelectBar: (Int) -> Unit,
    /** History over ISO days [from] to [to], narrowed to [categoryId] when a row was tapped. */
    onOpenHistory: (from: String, to: String, categoryId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.ptut.heroBg)) {
        when (uiState) {
            ReportsUiState.Loading -> Unit
            is ReportsUiState.Success -> LoadedReports(uiState, onChoosePeriod, onSelectBar, onOpenHistory)
        }
    }
}

@Composable
private fun LoadedReports(
    state: ReportsUiState.Success,
    onChoosePeriod: (ReportPeriod) -> Unit,
    onSelectBar: (Int) -> Unit,
    onOpenHistory: (from: String, to: String, categoryId: String?) -> Unit,
) {
    // A first tap selects a bar, as p14 draws; a tap on the selected one opens its days.
    val tapBar: (Int) -> Unit = { index ->
        val bar = state.bars[index]
        if (bar.isSelected && bar.opensHistory) onOpenHistory(bar.from, bar.to, null) else onSelectBar(index)
    }
    // The sheet reaches the bottom of the screen however few categories it holds, as p14 draws it.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        val density = LocalDensity.current
        var fieldHeight by remember { mutableIntStateOf(0) }
        val text = LocalCopy.current.reports
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.onSizeChanged { fieldHeight = it.height }) {
                SkyTopBar(title = text.title, onBack = null)
                PeriodTabs(
                    period = state.period,
                    onChoosePeriod = onChoosePeriod,
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )
                PeriodTotal(state = state, modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md))
                SpendChart(
                    state = state,
                    onSelectBar = tapBar,
                    modifier = Modifier.padding(horizontal = Spacing.sm).padding(top = Spacing.md, bottom = Spacing.md),
                )
            }
            SkyPageSheet(
                modifier = Modifier.heightIn(min = with(density) { (viewport - fieldHeight.toDp()).coerceAtLeast(Spacing.none) }),
            ) {
                if (state.hasTransactions) {
                    TopCategories(state.categories, onOpen = { onOpenHistory(state.from, state.to, it) })
                } else {
                    NothingToChart(state.period)
                }
            }
        }
    }
}

/** W / M / 3M / 6M / Y (p14): a translucent track on the sky, the chosen period a white pill. */
@Composable
private fun PeriodTabs(
    period: ReportPeriod,
    onChoosePeriod: (ReportPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.reports
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.periodTrack)
            .clip(CircleShape)
            .background(MaterialTheme.ptut.heroInner)
            .padding(SkySizes.periodInset)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReportPeriod.entries.forEach { option ->
            val chosen = option == period
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = SkySizes.periodTrack - SkySizes.periodInset * 2)
                    .clip(CircleShape)
                    .background(if (chosen) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent)
                    .semantics { contentDescription = option.spoken(text) }
                    .selectable(selected = chosen, role = Role.Tab, onClick = { onChoosePeriod(option) }),
            ) {
                Text(
                    text = option.short(text),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.ptut.heroOn,
                )
            }
        }
    }
}

@Composable
private fun PeriodTotal(state: ReportsUiState.Success, modifier: Modifier = Modifier) {
    val copy = LocalCopy.current
    val ink = MaterialTheme.ptut.heroOn
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(state.caption, style = MaterialTheme.typography.bodyLarge, color = ink)
        Text(
            text = state.total.format(),
            style = MaterialTheme.ptutType.report,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(state.periodLabel, style = MaterialTheme.typography.labelLarge, color = ink)
        // In ink with its arrow: Sky keeps red for delete and stale data (#147, R13).
        state.delta?.let { delta ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Icon(
                    imageVector = when (delta.direction) {
                        TrendDirection.Up -> PtutIcon.TrendingUp.image
                        TrendDirection.Down -> PtutIcon.TrendingDown.image
                        TrendDirection.Flat -> PtutIcon.TrendingFlat.image
                    },
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(Spacing.sm),
                )
                Text(delta.text, style = MaterialTheme.typography.labelLarge, color = ink)
            }
        }
        // Income and Net, which no other Sky screen shows (#146, K1).
        Row(
            modifier = Modifier.padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            FieldTile(copy.common.income, state.income.format(), Modifier.weight(1f))
            FieldTile(copy.reports.net, state.net.formatNet(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun FieldTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(SkySizes.chartBarCorner * 2))
            .background(MaterialTheme.ptut.heroInner)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.s12, vertical = Spacing.xs),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.ptut.heroOn)
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.ptut.heroOn)
    }
}

/**
 * The bars, the dashed average and the axis (p14). The selected bar wears an ink edge and a
 * hatch, and its own figure sits over it. Tapping anywhere in a bar's column selects it: the
 * month's 31 columns are too narrow to be targets of their own, so the chart is the one target.
 */
@Composable
private fun SpendChart(
    state: ReportsUiState.Success,
    onSelectBar: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bars = state.bars
    if (bars.isEmpty()) return
    val gap = when {
        bars.size <= 7 -> Spacing.xs
        bars.size <= 13 -> Spacing.xxs
        else -> Spacing.s2
    }
    val motion = MaterialTheme.ptutMotion
    val grow = motion.chart.orStill()
    // The tap gesture outlives a recomposition, and the tap now depends on which bar is selected.
    val tap by rememberUpdatedState(onSelectBar)
    // Grows off the axis once per period, never on scroll (page 03).
    val grown = remember(state.period) { Animatable(0f) }
    LaunchedEffect(state.period) { grown.animateTo(1f, tween(grow, easing = motion.standard)) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SkySizes.chart)
                .pointerInput(bars.size) {
                    detectTapGestures { at -> tap((at.x / size.width * bars.size).toInt().coerceIn(0, bars.lastIndex)) }
                },
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEachIndexed { index, bar ->
                    ChartBar(
                        bar = bar,
                        grown = grown.value,
                        onTap = { onSelectBar(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            state.average?.let { AverageLine(it) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            bars.forEach { bar ->
                Text(
                    text = bar.axis,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.ptut.heroOn,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ChartBar(bar: BarUiModel, grown: Float, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.ptut.heroOn
    val opens = LocalCopy.current.reports.showInHistory.takeIf { bar.isSelected && bar.opensHistory }
    val fill = MaterialTheme.ptut.heroInner
    val shape = RoundedCornerShape(topStart = SkySizes.chartBarCorner, topEnd = SkySizes.chartBarCorner)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .semantics {
                contentDescription = "${bar.name}, ${bar.amount}"
                selected = bar.isSelected
                onClick(label = opens) {
                    onTap()
                    true
                }
            },
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bar.isSelected) {
            Text(bar.amount, style = MaterialTheme.typography.labelSmall, color = ink, maxLines = 1, softWrap = false)
        }
        Box(
            modifier = Modifier
                .padding(top = Spacing.xxs)
                .fillMaxWidth()
                // A stub floor in dp: an empty bucket is drawn short, never as a gap (p15).
                .height(maxOf(SkySizes.chartStub, (SkySizes.chart - SkySizes.chartHeadroom) * bar.fraction))
                .graphicsLayer {
                    scaleY = grown
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clip(shape)
                .background(fill)
                .then(
                    if (bar.isSelected) {
                        Modifier
                            .drawBehind {
                                val step = SkySizes.chartHatch.toPx()
                                clipRect {
                                    var x = -size.height
                                    while (x < size.width) {
                                        drawLine(ink.copy(alpha = HATCH_ALPHA), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = SkySizes.chartEdge.toPx())
                                        x += step
                                    }
                                }
                            }
                            .border(SkySizes.chartEdge, ink, shape)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

@Composable
private fun AverageLine(average: AverageUiModel) {
    val ink = MaterialTheme.ptut.heroOn
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val band = maxHeight - SkySizes.chartHeadroom
        val y = maxHeight - band * average.fraction
        Canvas(Modifier.fillMaxSize()) {
            val at = y.toPx()
            drawLine(
                color = ink,
                start = Offset(0f, at),
                end = Offset(size.width, at),
                strokeWidth = SkySizes.chartEdge.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)),
            )
        }
        Text(
            text = average.text,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = (y - SkySizes.chartLabelLift).coerceAtLeast(Spacing.none)),
        )
    }
}

/**
 * "Top categories" (p14): the proportion bar, then one row per category. No chevron (#147, S9), but
 * a row opens its purchases in History (#220).
 */
@Composable
private fun TopCategories(categories: List<CategoryRowUiModel>, onOpen: (String) -> Unit) {
    val text = LocalCopy.current.reports
    SkySectionHeader(text.topCategories)
    if (categories.isEmpty()) {
        // Income only: the rows exist, and none of them is spending.
        Text(
            text = text.nothingSpent,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs, bottom = SkySizes.pillClearance),
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs, bottom = Spacing.s12),
        horizontalArrangement = Arrangement.spacedBy(SkySizes.shareGap),
    ) {
        categories.forEach { row ->
            Box(
                Modifier
                    .weight(row.fraction)
                    .height(SkySizes.shareBar)
                    .clip(CircleShape)
                    .background(categoryTint(row.color).fill),
            )
        }
    }
    Column(modifier = Modifier.padding(bottom = SkySizes.pillClearance)) {
        categories.forEach { row ->
            CategoryRow(row, onClick = { onOpen(row.id) })
            row.subCategories.forEach { sub -> SubCategoryRow(sub) }
        }
    }
}

@Composable
private fun CategoryRow(row: CategoryRowUiModel, onClick: () -> Unit) {
    val tint = categoryTint(row.color)
    val type = MaterialTheme.ptutType
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.transactionRow)
            .tappable(PtutRadii.r12, Role.Button, onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(SkySizes.rowCircle).clip(CircleShape).background(tint.fill),
        ) {
            Icon(ptutIcon(row.icon), contentDescription = null, tint = tint.on, modifier = Modifier.size(SkySizes.rowGlyph))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, style = type.listHeadline, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Its own movement against last period (#146, K3), in ink like the delta (R13).
            row.trend?.let { trend ->
                Text("${trend.text} · ${row.share}%", style = type.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } ?: Text("${row.share}%", style = type.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(row.amount, style = type.listHeadline, color = MaterialTheme.colorScheme.onSurface)
            Text(row.purchases, style = type.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One line of a parent's split, at lower weight under it (p34): the name lines up with the
 * parent's name, so the top-level answer stays the easiest thing on the sheet to read.
 */
@Composable
private fun SubCategoryRow(sub: SubCategorySpendUiModel) {
    val type = MaterialTheme.ptutType
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(start = SkySizes.rowCircle + Spacing.sm, bottom = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            sub.name,
            style = type.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(sub.amount, style = type.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** p15: the same sheet with a sentence instead of rows, and no button, because waiting is right. */
@Composable
private fun NothingToChart(period: ReportPeriod) {
    val text = LocalCopy.current.reports
    Text(
        text = text.nothingToChart,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = text.emptyBody(period.bucketNoun(text)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs),
    )
    // FR-20: an empty screen says the importer is still at work.
    Row(
        modifier = Modifier.padding(top = Spacing.sm, bottom = SkySizes.pillClearance),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PtutIcon.Schedule.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Spacing.sm))
        Text(text.buildsItself, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun ReportPeriod.short(text: ReportsCopy): String = when (this) {
    ReportPeriod.Week -> text.tabWeek
    ReportPeriod.Month -> text.tabMonth
    ReportPeriod.ThreeMonths -> text.tabThreeMonths
    ReportPeriod.SixMonths -> text.tabSixMonths
    ReportPeriod.Year -> text.tabYear
}

private fun ReportPeriod.spoken(text: ReportsCopy): String = when (this) {
    ReportPeriod.Week -> text.spokenWeek
    ReportPeriod.Month -> text.spokenMonth
    ReportPeriod.ThreeMonths -> text.threeMonths
    ReportPeriod.SixMonths -> text.sixMonths
    ReportPeriod.Year -> text.spokenYear
}

private fun ReportPeriod.bucketNoun(text: ReportsCopy): String = when (this) {
    ReportPeriod.Week, ReportPeriod.Month -> text.bucketDay
    ReportPeriod.ThreeMonths, ReportPeriod.SixMonths -> text.bucketWeek
    ReportPeriod.Year -> text.bucketMonth
}

private const val DASH = 8f
private const val HATCH_ALPHA = 0.35f
