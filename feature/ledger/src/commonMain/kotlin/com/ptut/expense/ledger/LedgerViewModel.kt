package com.ptut.expense.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.DateRange
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.LedgerFilter
import com.ptut.expense.domain.MetaParts
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.UnknownCategory
import com.ptut.expense.domain.accepts
import com.ptut.expense.domain.bucketByDay
import com.ptut.expense.domain.colorName
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.displayName
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.formatNet
import com.ptut.expense.domain.iconName
import com.ptut.expense.domain.label
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.matches
import com.ptut.expense.domain.metaLine
import com.ptut.expense.domain.metaParts
import com.ptut.expense.domain.rules.ShopNames
import com.ptut.expense.domain.weekdayAndDay
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
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
    val date: String,
    val total: String,
    val rows: List<LedgerRowUiModel>,
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
    ) : LedgerUiState {
        val isEmpty: Boolean get() = days.isEmpty()

        /** Nothing is hidden and nothing shows: the ledger itself is empty. */
        val ledgerIsEmpty: Boolean get() = isEmpty && hiddenByFilter == 0
    }
}

/**
 * FR-21. The flat read of the table every later number gets checked against.
 *
 * It reads [LedgerRepository.allTransactions] rather than the reports window: an audit list that
 * silently stopped at a horizon is how a month stops adding up with nothing on screen to say why.
 *
 * The search text and the chip stay here rather than in the view, so the day grouping is computed
 * once per change instead of on every recomposition of a scrolling list.
 */
class LedgerViewModel(
    ledger: LedgerRepository,
    categories: List<Category> = Category.seed(),
    /**
     * #115: the reader's categories as they change. A tab keeps its holder while the reader is
     * away, so a list read once would keep an old name, or drop a new category, until a restart.
     */
    categoryUpdates: Flow<List<Category>> = flowOf(categories),
    /** The sub-categories as they change, for the same reason (#112). */
    subCategoryUpdates: Flow<List<SubCategory>> = flowOf(emptyList()),
    /** Only to know which group is today's, "Today · Fri 14" (#128). */
    private val clock: Clock = Clock.System,
    /** The slice Reports opened (#220), or null for the whole table. Search and filters work inside it. */
    private val scope: LedgerScope? = null,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val chosen = MutableStateFlow(LedgerFilter.All)

    val uiState: StateFlow<LedgerUiState> =
        combine(
            ledger.allTransactions,
            query,
            chosen,
            // ShopNames too: naming a shop read as noise retitles its rows here without a restart.
            combine(categoryUpdates, subCategoryUpdates, ShopNames.names) { c, s, _ -> c to s },
        ) { snapshot, text, filter, (categories, subCategories) ->
            val transactions = when (snapshot) {
                LedgerSnapshot.Unanswered -> return@combine LedgerUiState.Loading
                is LedgerSnapshot.Rows -> snapshot.transactions
            }
            val inScope = scope?.let { slice -> transactions.filter { slice.holds(it, categories) } } ?: transactions
            val visible = inScope.filter { filter.accepts(it) && it.matches(text) }
            val today = clock.now().toLocalDateTime(BangkokTime).date

            LedgerUiState.Success(
                query = text,
                filter = filter,
                days = visible.bucketByDay(newestFirst = true).map { bucket ->
                    LedgerDayUiModel(
                        date = if (bucket.date == today) "${AppText.copy.common.today} · ${bucket.date.weekdayAndDay()}" else bucket.date.dayLabel(),
                        total = bucket.summary.net.formatNet(),
                        rows = bucket.transactions
                            .sortedByDescending(Transaction::occurredAtEpochMillis)
                            .map { it.toRow(categories, subCategories) },
                    )
                },
                hiddenByFilter = inScope.size - visible.size,
                scope = scope?.heading(categories),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LedgerUiState.Loading,
        )

    fun search(text: String) {
        query.value = text
    }

    fun filter(choice: LedgerFilter) {
        chosen.value = choice
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

private fun LedgerScope.holds(transaction: Transaction, categories: List<Category>): Boolean {
    if (transaction.occurredAtEpochMillis.dateIn(BangkokTime) !in DateRange(from, to)) return false
    val wanted = categoryId ?: return true
    val filed = categories.firstOrNull { it.id == transaction.categoryId } ?: UnknownCategory
    return transaction.direction == Direction.Expense && filed.id == wanted
}

private fun LedgerScope.heading(categories: List<Category>) = LedgerScopeUiModel(
    title = categoryId?.let { id -> (categories.firstOrNull { it.id == id } ?: UnknownCategory).name } ?: AppText.copy.history.title,
    days = if (from == to) from.dayLabel() else DateRange(from, to).label(),
)
