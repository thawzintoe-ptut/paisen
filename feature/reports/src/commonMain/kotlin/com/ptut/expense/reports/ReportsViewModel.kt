package com.ptut.expense.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.BucketSize
import com.ptut.expense.domain.BucketSpend
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.ReportPeriod
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Separators
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Trend
import com.ptut.expense.domain.TrendDirection
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.abbreviated
import com.ptut.expense.domain.bucketSize
import com.ptut.expense.domain.dayAndMonth
import com.ptut.expense.domain.format
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.inCalendarDigits
import com.ptut.expense.domain.label
import com.ptut.expense.domain.monthShort
import com.ptut.expense.domain.reports.CategoryComparison
import com.ptut.expense.domain.reports.GetPeriodReportUseCase
import com.ptut.expense.domain.reports.PeriodReport
import com.ptut.expense.domain.weekdayAndDay
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.targetCopy
import com.ptut.expense.ui.SpendWords
import com.ptut.expense.ui.phrase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

/** A signed movement against the period before. Carries its direction so the UI never has to
 *  infer meaning from the colour alone. */
data class DeltaUiModel(val text: String, val direction: TrendDirection)

/** One bar of the chart (#164): a day, a week or a month, by period. */
data class BarUiModel(
    /** Under the bar: "M", "15", "6 Oct" or "O". Blank where a label on every bar would crowd. */
    val axis: String,
    /**
     * The bar's own figure, abbreviated. An empty bucket is a dash, not a zero: nothing was
     * recorded, which is not the same claim as nothing was spent.
     */
    val amount: String,
    /** The bucket in words, for the caption over the total: "Fri 14", "Week of 6 Oct", "Oct". */
    val name: String,
    /** 0f–1f of the tallest bar in the period. */
    val fraction: Float,
    /** The bucket today falls in. */
    val isCurrent: Boolean,
    val isSelected: Boolean,
    /** The bucket's first and last day, ISO, for the History it opens (#220). */
    val from: String,
    val to: String,
    /** Only a bar with spending opens History: an empty one has no rows to show. */
    val opensHistory: Boolean,
)

/** The dashed line (p14): "avg ฿836", at [fraction] of the tallest bar. */
data class AverageUiModel(val text: String, val fraction: Float)

/**
 * A category's movement against the same category last period.
 *
 * Null when there is no baseline — a category that had no spending before cannot be a
 * percentage of itself, and inventing one is the kind of confident wrong number FR-20 exists
 * to keep off the screen.
 */
data class CategoryTrendUiModel(val text: String, val direction: TrendDirection)

data class CategoryRowUiModel(
    /** The category's id, so a tap can open its purchases in History (#220). */
    val id: String,
    val name: String,
    val icon: String,
    /** The category's own tint, so the breakdown reads in the same colours as the ledger. */
    val color: String,
    val amount: String,
    val share: Int,
    /** The category's part of the period's spending, for its length in the proportion bar. */
    val fraction: Float,
    val trend: CategoryTrendUiModel?,
    /** "9 purchases" (p14). */
    val purchases: String,
    /** The split under it, at lower weight (p34). Empty when no row here has a sub-category. */
    val subCategories: List<SubCategorySpendUiModel> = emptyList(),
    /** #21: "of ฿2,500" on the week period, for the one category with a target. */
    val target: String? = null,
)

data class SubCategorySpendUiModel(val name: String, val amount: String)

sealed interface ReportsUiState {
    data object Loading : ReportsUiState

    data class Success(
        val period: ReportPeriod,
        /** "Spent Mon 6 – Sun 12": the dates the total covers. */
        val periodLabel: String,
        /** The period's first and last day, ISO, for a category row's History (#220). */
        val from: String,
        val to: String,
        /** "This week · Fri 14": the period, then the bar the reader has selected (p14). */
        val caption: String,
        val total: Satang,
        val income: Satang,
        val net: Satang,
        /** Null when there is no earlier period to measure against — never an invented zero. */
        val delta: DeltaUiModel?,
        /**
         * Whether the period holds any rows at all. The empty state cannot be read off [total]:
         * that is expense only, so a week of nothing but income would claim nothing was recorded.
         */
        val hasTransactions: Boolean,
        val bars: List<BarUiModel>,
        /** Null when nothing was spent: a line at zero would only say the chart is empty twice. */
        val average: AverageUiModel?,
        val categories: List<CategoryRowUiModel>,
    ) : ReportsUiState
}

class ReportsViewModel(
    getPeriodReport: GetPeriodReportUseCase,
    categories: List<Category> = Category.seed(),
    /**
     * #115: the reader's categories as they change. A tab keeps its holder while the reader is
     * away, so a list read once would keep an old name, or drop a new category, until a restart.
     */
    categoryUpdates: Flow<List<Category>> = flowOf(categories),
    /** The sub-categories as they change, for the same reason (#112). */
    subCategoryUpdates: Flow<List<SubCategory>> = flowOf(emptyList()),
    /** #21: the one weekly target, or null with none set. */
    weeklyTarget: Flow<WeeklyTarget?> = flowOf(null),
) : ViewModel() {

    private val period = MutableStateFlow(ReportPeriod.Week)

    /** The bar the reader tapped, or null for the one today falls in. */
    private val selected = MutableStateFlow<Int?>(null)

    // The sums run off the main thread in the use case; the words for a few bars and rows stay here.
    val uiState: StateFlow<ReportsUiState> =
        combine(getPeriodReport(period, categoryUpdates, subCategoryUpdates, weeklyTarget, SpendWords), selected) { report, picked ->
            report?.toUiState(picked) ?: ReportsUiState.Loading
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS),
                initialValue = ReportsUiState.Loading,
            )

    fun show(period: ReportPeriod) {
        selected.value = null
        this.period.value = period
    }

    /** A tap on a bar. The total stays the period's; the caption and the bar's own figure follow the tap. */
    fun select(bar: Int) {
        selected.value = bar
    }
}

/** The total stays the period's; the caption and the bar's own figure follow [picked]. */
private fun PeriodReport.toUiState(picked: Int?): ReportsUiState.Success {
    val chosenBar = picked?.takeIf { it in buckets.indices } ?: current
    val text = AppText.copy.reports
    return ReportsUiState.Success(
        period = period,
        periodLabel = text.spent(range.label()),
        from = range.start.toString(),
        to = range.endInclusive.toString(),
        caption = listOfNotNull(period.title(), buckets.getOrNull(chosenBar)?.name(period.bucketSize))
            .joinToString(Separators.DOT),
        total = summary.expense,
        income = summary.income,
        net = summary.net,
        delta = delta(chosen = period, trend = trend),
        hasTransactions = hasTransactions,
        bars = bars(buckets, period, current = current, chosenBar = chosenBar, tallest = tallest),
        average = average?.let { AverageUiModel(text = text.average(it.formatCompact()), fraction = it.ratioTo(tallest)) },
        categories = categoryRows(categories, summary.expense),
    )
}

private fun bars(
    buckets: List<BucketSpend>,
    chosen: ReportPeriod,
    current: Int,
    chosenBar: Int,
    tallest: Satang,
): List<BarUiModel> = buckets.mapIndexed { index, bucket ->
    BarUiModel(
        axis = bucket.axis(chosen, index, buckets.size),
        amount = if (bucket.spent == Satang.Zero) Separators.DASH else bucket.spent.abbreviated(),
        name = bucket.name(chosen.bucketSize),
        fraction = bucket.spent.ratioTo(tallest),
        isCurrent = index == current,
        isSelected = index == chosenBar,
        from = bucket.start.toString(),
        to = bucket.endInclusive.toString(),
        opensHistory = bucket.spent > Satang.Zero,
    )
}

private fun categoryRows(comparisons: List<CategoryComparison>, spent: Satang): List<CategoryRowUiModel> {
    val text = AppText.copy.reports
    return comparisons.map { (spend, trend, target) ->
        CategoryRowUiModel(
            id = spend.category.id,
            name = spend.category.name,
            icon = spend.category.icon,
            color = spend.category.color,
            amount = spend.total.format(),
            share = spend.share,
            fraction = spend.total.ratioTo(spent),
            trend = categoryTrend(trend),
            purchases = text.purchases(spend.count),
            subCategories = spend.subCategories.map { sub -> SubCategorySpendUiModel(sub.name, sub.total.format()) },
            target = target?.let { targetCopy().ofTarget(it.formatCompact()) },
        )
    }
}

/** The period in the words p14 uses over the total. */
private fun ReportPeriod.title(): String = when (this) {
    ReportPeriod.Week -> AppText.copy.common.thisWeek
    ReportPeriod.Month -> AppText.copy.common.thisMonth
    ReportPeriod.ThreeMonths -> AppText.copy.reports.threeMonths
    ReportPeriod.SixMonths -> AppText.copy.reports.sixMonths
    ReportPeriod.Year -> AppText.copy.reports.twelveMonths
}

private fun BucketSpend.name(size: BucketSize): String = when (size) {
    BucketSize.Day -> start.weekdayAndDay()
    BucketSize.Week -> AppText.copy.reports.weekOf(start.dayAndMonth())
    BucketSize.Month -> start.monthShort()
}

/**
 * A week labels every day by its initial, as p14 draws. A month labels a day a week, the long
 * periods about four of their weeks, and a year every month by its initial, so no two labels touch.
 */
private fun BucketSpend.axis(period: ReportPeriod, index: Int, count: Int): String = when (period) {
    ReportPeriod.Week -> AppText.copy.reports.weekAxis[start.dayOfWeek.isoDayNumber - 1]

    ReportPeriod.Month -> if ((start.day - 1) % DAYS_IN_WEEK == 0) start.day.inCalendarDigits() else ""

    ReportPeriod.ThreeMonths, ReportPeriod.SixMonths ->
        if (index % ((count + LONG_AXIS_LABELS - 1) / LONG_AXIS_LABELS) == 0) start.dayAndMonth() else ""

    ReportPeriod.Year -> AppText.copy.reports.yearAxis[start.month.number - 1]
}

private fun delta(chosen: ReportPeriod, trend: Trend): DeltaUiModel? {
    val text = AppText.copy.reports
    val baseline = when (chosen) {
        ReportPeriod.Week -> text.lastWeek
        ReportPeriod.Month -> text.lastMonth
        ReportPeriod.ThreeMonths -> text.threeMonthsBefore
        ReportPeriod.SixMonths -> text.sixMonthsBefore
        ReportPeriod.Year -> text.yearBefore
    }
    // Shared with Activity's weekly wrap, so the two never word a change differently (#127).
    val phrase = trend.phrase(baseline) ?: return null
    return DeltaUiModel(text = phrase, direction = trend.direction)
}

/**
 * Signed the way the reader reads it, not the way the number is stored: a rise carries a plus
 * and a fall carries a U+2212 minus, so the text says the direction even where the colour cannot.
 */
private fun categoryTrend(trend: Trend): CategoryTrendUiModel? {
    val percent = trend.percent ?: return null
    return CategoryTrendUiModel(
        text = when (trend.direction) {
            TrendDirection.Flat -> AppText.copy.reports.same
            TrendDirection.Up -> "+$percent%"
            TrendDirection.Down -> "−${-percent}%"
        },
        direction = trend.direction,
    )
}

private const val DAYS_IN_WEEK = 7

/** About this many week labels on a 13 or 26 week chart. */
private const val LONG_AXIS_LABELS = 4
