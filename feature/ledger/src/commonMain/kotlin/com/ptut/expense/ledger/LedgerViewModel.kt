package com.ptut.expense.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.DateRange
import com.ptut.expense.domain.DayBucket
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.LedgerFilter
import com.ptut.expense.domain.MetaParts
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.Separators
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.colorName
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.formatNet
import com.ptut.expense.domain.iconName
import com.ptut.expense.domain.label
import com.ptut.expense.domain.longName
import com.ptut.expense.domain.monthShort
import com.ptut.expense.domain.rules.ShopNames
import com.ptut.expense.domain.today.FilterLedgerUseCase
import com.ptut.expense.domain.today.FilteredLedger
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import com.ptut.expense.domain.today.LedgerSlice
import com.ptut.expense.domain.weekdayAndDay
import com.ptut.expense.domain.yearLabel
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.SpendWords
import com.ptut.expense.ui.UnknownCategory
import com.ptut.expense.ui.displayName
import com.ptut.expense.ui.metaLine
import com.ptut.expense.ui.metaParts
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

data class LedgerRowUiModel(
    val id: String,
    val merchant: String,
    val meta: String,
    /** [meta]'s parts, for a Sky row that draws the time under the amount (#156). */
    val parts: MetaParts,
    val amount: String,
    val icon: String,
    val color: String,
    val direction: Direction,
)

/** A day header and its rows. [total] is the day's net, signed, never a bare magnitude. */
data class LedgerDayUiModel(
    /** The list key of the day header, from the date itself so no two years share one. */
    val key: String,
    val date: String,
    val total: String,
    val rows: List<LedgerRowUiModel>,
)

/**
 * One month on History's edge index (#70), newest first. [dayKey] is the key of the month's first
 * day header, so the list jumps by key; [keys] are every day and row key in the month, so the list
 * can tell which month is under the top line, which [behind] then describes.
 */
data class LedgerMonthUiModel(
    val label: String,
    /** Shown on the newest month and wherever the year changes. */
    val year: String?,
    val spoken: String,
    val dayKey: String,
    val keys: Set<String>,
    val behind: String,
    /** #98: the month was closed and sealed; its rows can still be edited. */
    val sealed: Boolean = false,
)

/**
 * What Reports opened History on (#220): the days [from] to [to], and the one category a row named.
 * The category matches as Reports counts it, spending only, so the two screens agree on a row's count.
 */
data class LedgerScope(val from: LocalDate, val to: LocalDate, val categoryId: String? = null)

/** A scoped History's heading: the category, or "History", over the days it covers. */
data class LedgerScopeUiModel(val title: String, val days: String)

sealed interface LedgerUiState {
    data object Loading : LedgerUiState

    data class Success(
        val query: String,
        val filter: LedgerFilter,
        val days: List<LedgerDayUiModel>,
        /**
         * How many rows the ledger holds that this filter and search hide.
         *
         * The empty state needs it to tell the two emptinesses apart: a ledger with nothing in it
         * at all, and a ledger whose rows are simply all behind the chip the reader tapped. The
         * second is not an absence, and saying "no transactions" there would be a lie.
         */
        val hiddenByFilter: Int,
        /** Null on the History Home opens, which shows the whole table. */
        val scope: LedgerScopeUiModel? = null,
        val months: List<LedgerMonthUiModel> = emptyList(),
    ) : LedgerUiState {
        val isEmpty: Boolean get() = days.isEmpty()

        /** One month has nothing to jump between. */
        val hasMonthIndex: Boolean get() = months.size > 1

        /** The month holding the day or row with [key]; the newest month above the first day. */
        fun monthOf(key: Any?): LedgerMonthUiModel? = months.firstOrNull { key in it.keys } ?: months.firstOrNull()

        /** Nothing is hidden and nothing shows: the ledger itself is empty. */
        val ledgerIsEmpty: Boolean get() = isEmpty && hiddenByFilter == 0
    }
}

/**
 * FR-21. The flat read of the table every later number gets checked against.
 *
 * The search text and the chip stay here rather than in the view, so the day grouping is computed
 * once per change instead of on every recomposition of a scrolling list.
 */
class LedgerViewModel(
    filterLedger: FilterLedgerUseCase,
    categories: List<Category> = Category.seed(),
    /**
     * #115: the reader's categories as they change. A tab keeps its holder while the reader is
     * away, so a list read once would keep an old name, or drop a new category, until a restart.
     */
    categoryUpdates: Flow<List<Category>> = flowOf(categories),
    /** The sub-categories as they change, for the same reason (#112). */
    subCategoryUpdates: Flow<List<SubCategory>> = flowOf(emptyList()),
    /** Only to know which group is today's, "Today · Fri 14" (#128). */
    clock: Clock = Clock.System,
    private val homeToday: GetHomeTodayUseCase = GetHomeTodayUseCase(clock),
    /** The slice Reports opened (#220), or null for the whole table. Search and filters work inside it. */
    private val scope: LedgerScope? = null,
    /** Turning a whole ledger into rows on every keystroke stays off the main thread too. */
    private val work: CoroutineDispatcher = Dispatchers.Default,
    /** #98: the months closed and sealed, ISO `yyyy-MM`; none where play is off. */
    sealedMonths: Flow<Set<String>> = flowOf(emptySet()),
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val chosen = MutableStateFlow(LedgerFilter.All)

    val uiState: StateFlow<LedgerUiState> =
        combine(
            filterLedger(query, chosen, categoryUpdates, SpendWords, scope?.let { LedgerSlice(DateRange(it.from, it.to), it.categoryId) }),
            // ShopNames too: naming a shop read as noise retitles its rows here without a restart.
            combine(subCategoryUpdates, ShopNames.names) { subCategories, _ -> subCategories },
            sealedMonths,
        ) { filtered, subCategories, sealed ->
            filtered?.let { success(it, subCategories, sealed) } ?: LedgerUiState.Loading
        }.flowOn(work).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS),
            initialValue = LedgerUiState.Loading,
        )

    fun search(text: String) {
        query.value = text
    }

    fun filter(choice: LedgerFilter) {
        chosen.value = choice
    }

    private fun success(filtered: FilteredLedger, subCategories: List<SubCategory>, sealed: Set<String>): LedgerUiState.Success {
        val today = homeToday()
        val categories = filtered.categories
        return LedgerUiState.Success(
            query = filtered.query,
            filter = filtered.filter,
            days = filtered.days.map { bucket ->
                LedgerDayUiModel(
                    key = bucket.date.dayKey(),
                    date = if (bucket.date == today) "${AppText.copy.common.today}${Separators.DOT}${bucket.date.weekdayAndDay()}" else bucket.date.dayLabel(),
                    total = bucket.summary.net.formatNet(),
                    rows = bucket.transactions.map { it.toRow(categories, subCategories) },
                )
            },
            hiddenByFilter = filtered.hiddenByFilter,
            scope = scope?.heading(categories),
            months = filtered.days.monthIndex().map { month -> month.takeUnless { month.isoMonth in sealed } ?: month.sealedCopy() },
        )
    }

    private fun Transaction.toRow(categories: List<Category>, subCategories: List<SubCategory>) = LedgerRowUiModel(
        id = id,
        merchant = displayName(categories, subCategories),
        meta = metaLine(categories, subCategories = subCategories),
        parts = metaParts(categories, subCategories = subCategories),
        // No sign and no empty satang: the row draws spending in ruby and income in green.
        amount = amount.formatCompact(),
        icon = iconName(categories),
        color = colorName(categories),
        direction = direction,
    )
}

private fun List<DayBucket>.monthIndex(): List<LedgerMonthUiModel> {
    // Newest first, so each month's days sit together and keep their order.
    val months = groupBy { it.date.year to it.date.month }.values.toList()
    val total = sumOf { it.transactions.size }
    val text = AppText.copy.history
    var rowsAbove = 0
    return months.mapIndexed { index, days ->
        val date = days.first().date
        val month = LedgerMonthUiModel(
            label = date.monthShort(),
            year = date.yearLabel().takeIf { index == 0 || months[index - 1].first().date.year != date.year },
            spoken = text.goToMonth("${date.month.longName()} ${date.yearLabel()}"),
            dayKey = date.dayKey(),
            keys = days.flatMapTo(mutableSetOf()) { day -> listOf(day.date.dayKey()) + day.transactions.map(Transaction::id) },
            behind = if (index == 0) text.monthsTotal(total, months.size) else text.monthsAbove(rowsAbove, index),
        )
        rowsAbove += days.sumOf { it.transactions.size }
        month
    }
}

private fun LocalDate.dayKey(): String = "$DAY_KEY$this"

/** A month's ISO `yyyy-MM`, read off its first day's key. */
private val LedgerMonthUiModel.isoMonth: String get() = dayKey.removePrefix(DAY_KEY).take(ISO_MONTH)

private fun LedgerMonthUiModel.sealedCopy() = copy(sealed = true, spoken = AppText.copy.play.extras.sealedMonth(spoken))

private const val DAY_KEY = "day-"
private const val ISO_MONTH = 7

private fun LedgerScope.heading(categories: List<Category>) = LedgerScopeUiModel(
    title = categoryId?.let { id -> (categories.firstOrNull { it.id == id } ?: UnknownCategory).name } ?: AppText.copy.history.title,
    days = if (from == to) from.dayLabel() else DateRange(from, to).label(),
)
