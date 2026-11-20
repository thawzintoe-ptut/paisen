package com.ptut.expense.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.categories.CategorySpend
import com.ptut.expense.domain.categories.MoveCategoryUseCase
import com.ptut.expense.domain.categories.ObserveMonthlySpendByCategoryUseCase
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * FR-12, FR-13 and US-9: the categories are the reader's, and the screen that says so also says
 * what each one cost this month.
 *
 * Spend is read from the transactions every time and never stored; [ObserveMonthlySpendByCategoryUseCase]
 * says why.
 */
@OptIn(ExperimentalUuidApi::class)
class CategoriesViewModel(
    observeMonthlySpend: ObserveMonthlySpendByCategoryUseCase,
    private val moveCategory: MoveCategoryUseCase,
    /** Injected so a test can name the row it wrote. Random everywhere else. */
    private val newId: () -> String = { Uuid.random().toString() },
    /** Where a failed move is said. Null in a graph that binds none. */
    private val toasts: ToastMessages? = null,
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

    /** Moves one row one place; see [MoveCategoryUseCase] for why the whole order is written. */
    fun move(categoryId: String, by: Int) = viewModelScope.launch {
        moveCategory(categoryId, by)
            .onFailure { toasts?.show(ToastMessage(AppText.copy.settings.didNotWork, tone = ToastTone.Error)) }
    }

    fun openedCreated() {
        opened.value = null
    }

    val uiState: StateFlow<CategoriesUiState> = observeMonthlySpend()
        .map(::state)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS),
            initialValue = CategoriesUiState(),
        )

    // Unanswered is not an empty ledger, and a screen of "฿0.00" would say it was.
    private fun state(spend: List<CategorySpend>?): CategoriesUiState =
        spend?.let { CategoriesUiState(rows(it), loaded = true) } ?: CategoriesUiState()

    private fun rows(spend: List<CategorySpend>): List<CategoryRowUiModel> {
        val text = AppText.copy.categories
        return spend.mapIndexed { place, (category, mine, spentThisMonth) ->
            val spent = spentThisMonth.formatCompact()
            CategoryRowUiModel(
                id = category.id,
                name = category.name,
                icon = category.icon,
                color = category.color,
                spentThisMonth = spent,
                meta = text.meta(text.subCount(mine.size), spent),
                subCategories = mine.map { it.name },
                canMoveUp = place > 0,
                canMoveDown = place < spend.lastIndex,
            )
        }
    }
}
