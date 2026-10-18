package com.ptut.expense.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.categories.CategoryRepository
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.ledger.LedgerSnapshot
import com.ptut.expense.domain.startOfDayEpochMillis
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * FR-12, FR-13 and US-9: the categories are the reader's, and the screen that says so also says
 * what each one cost this month.
 *
 * Spend is derived here and never stored. FR-18's rule that no edit rewrites a recorded amount is
 * only keepable if the totals are read from the transactions every time — a cached figure is one
 * a rename or a reassignment can leave behind.
 */
@OptIn(ExperimentalUuidApi::class)
class CategoriesViewModel(
    ledger: LedgerRepository,
    private val categories: CategoryRepository,
    private val clock: Clock = Clock.System,
    /** Injected so a test can name the row it wrote. Random everywhere else. */
    private val newId: () -> String = { Uuid.random().toString() },
) : ViewModel() {

    private val opened = MutableStateFlow<String?>(null)

    /** The id a new category will take, for the screen to push straight into editing. */
    val justCreated: StateFlow<String?> = opened.asStateFlow()

    /**
     * A new category is opened for editing at once, rather than asking for a name in a dialog
     * first. Nothing is written here: Edit category stores it on Save, or never (#116).
     */
    fun newCategory() {
        opened.value = newId()
    }

    /**
     * Moves one row one place and writes the whole order.
     *
     * Rewriting all of it rather than swapping two rows is what makes the first move work: every
     * seeded category can share a sortOrder, and swapping two zeroes changes nothing.
     */
    fun move(categoryId: String, by: Int) = viewModelScope.launch {
        val ordered = categories.categories.value.sortedWith(inTheReadersOrder)
        val from = ordered.indexOfFirst { it.id == categoryId }
        val to = from + by
        if (from < 0 || to !in ordered.indices) return@launch

        categories.reorder(ordered.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun openedCreated() {
        opened.value = null
    }

    val uiState: StateFlow<CategoriesUiState> = combine(
        ledger.allTransactions,
        categories.categories,
        categories.subCategories,
    ) { snapshot, known, subs -> state(snapshot, known, subs) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoriesUiState(),
        )

    private fun state(
        snapshot: LedgerSnapshot,
        known: List<Category>,
        subs: List<SubCategory>,
    ): CategoriesUiState = when (snapshot) {
        // Unanswered is not an empty ledger, and a screen of "฿0.00" would say it was.
        LedgerSnapshot.Unanswered -> CategoriesUiState()

        is LedgerSnapshot.Rows -> CategoriesUiState(rows(snapshot.transactions, known, subs), loaded = true)
    }

    private fun rows(
        transactions: List<Transaction>,
        known: List<Category>,
        subs: List<SubCategory>,
    ): List<CategoryRowUiModel> {
        val thisMonth = transactions.filter { it.occurredAtEpochMillis >= startOfThisMonth() }
        val byCategory = subs.groupBy { it.categoryId }

        val ordered = known.sortedWith(inTheReadersOrder)
        val text = AppText.copy.categories

        return ordered.mapIndexed { place, category ->
            val mine = byCategory[category.id].orEmpty()
            val spent = thisMonth.spentUnder(category.id).formatCompact()

            CategoryRowUiModel(
                id = category.id,
                name = category.name,
                icon = category.icon,
                color = category.color,
                spentThisMonth = spent,
                meta = text.meta(text.subCount(mine.size), spent),
                subCategories = mine.map { it.name },
                canMoveUp = place > 0,
                canMoveDown = place < ordered.lastIndex,
            )
        }
    }

    /**
     * Spending only. An income category's rows are money in, and adding them to a "spent" figure
     * would make the one category that earns look like the one that costs most.
     */
    private fun List<Transaction>.spentUnder(categoryId: String): Satang = filter {
        it.categoryId == categoryId && it.direction == Direction.Expense
    }.fold(Satang.ofMinorUnits(0)) { running, row -> running + row.amount }

    /** FR-19: months break on the Bangkok calendar, like every other bucket in the app. */
    private fun startOfThisMonth(): Long {
        val today = clock.now().toLocalDateTime(BangkokTime).date
        return LocalDate(today.year, today.month, 1).startOfDayEpochMillis()
    }
}

/**
 * The order the screen shows and the order a move is computed against, kept in one place so the
 * two cannot drift. It mirrors `ORDER BY sortOrder, name` — the list a reader drags is the list
 * the query returns, or a move lands one row away from where they aimed it.
 */
internal val inTheReadersOrder: Comparator<Category> =
    compareBy<Category> { it.sortOrder }.thenBy { it.name }
