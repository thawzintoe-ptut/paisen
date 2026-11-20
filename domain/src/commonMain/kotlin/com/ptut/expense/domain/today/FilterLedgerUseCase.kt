package com.ptut.expense.domain.today

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.DateRange
import com.ptut.expense.domain.DayBucket
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.LedgerFilter
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.accepts
import com.ptut.expense.domain.bucketByDay
import com.ptut.expense.domain.inside
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.matches
import com.ptut.expense.domain.reports.SpendNames
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/**
 * What Reports opened History on (#220): the days in [range], and the one category a row named.
 * The category matches as Reports counts it, spending only, so the two screens agree on a row's count.
 */
data class LedgerSlice(val range: DateRange, val categoryId: String? = null)

/** History's rows after the slice, the chip and the search, with the inputs they answer. */
data class FilteredLedger(
    val query: String,
    val filter: LedgerFilter,
    /** The categories the slice was cut with, so the rows are named from the same list. */
    val categories: List<Category>,
    /** By day, newest day first and newest row first inside it. */
    val days: List<DayBucket>,
    /** Rows in the slice that the filter and search hide: an empty list behind a chip is not an empty ledger. */
    val hiddenByFilter: Int,
)

/**
 * FR-21: History reads the whole table rather than the reports window. An audit list that silently
 * stopped at a horizon is how a month stops adding up with nothing on screen to say why.
 */
class FilterLedgerUseCase(
    private val ledger: LedgerRepository,
    /** Grouping a whole ledger on every keystroke stays off the main thread. */
    private val work: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Null until the ledger answers. [names] files unknown spend as Reports does. */
    operator fun invoke(
        query: Flow<String>,
        filter: Flow<LedgerFilter>,
        categories: Flow<List<Category>>,
        names: SpendNames,
        slice: LedgerSlice? = null,
    ): Flow<FilteredLedger?> =
        combine(ledger.allTransactions, query, filter, categories) { snapshot, text, chosen, filing ->
            when (snapshot) {
                LedgerSnapshot.Unanswered -> null
                is LedgerSnapshot.Rows -> snapshot.transactions.filtered(text, chosen, filing, names, slice)
            }
        }.flowOn(work)
}

private fun List<Transaction>.filtered(
    query: String,
    filter: LedgerFilter,
    categories: List<Category>,
    names: SpendNames,
    slice: LedgerSlice?,
): FilteredLedger {
    val inSlice = slice?.let { cut -> inside(cut.range).filter { cut.holds(it, categories, names) } } ?: this
    val visible = inSlice.filter { filter.accepts(it) && it.matches(query) }
    return FilteredLedger(
        query = query,
        filter = filter,
        categories = categories,
        days = visible.bucketByDay(newestFirst = true).map { day ->
            day.copy(transactions = day.transactions.sortedByDescending(Transaction::occurredAtEpochMillis))
        },
        hiddenByFilter = inSlice.size - visible.size,
    )
}

/** The category half of the slice; the days are cut by [inside] first. */
private fun LedgerSlice.holds(transaction: Transaction, categories: List<Category>, names: SpendNames): Boolean {
    val wanted = categoryId ?: return true
    val filed = categories.firstOrNull { it.id == transaction.categoryId } ?: names.unknown
    return transaction.direction == Direction.Expense && filed.id == wanted
}
