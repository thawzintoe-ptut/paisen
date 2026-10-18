package com.ptut.expense.shoprule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.forDirection
import com.ptut.expense.domain.rules.RememberedMerchant
import com.ptut.expense.domain.rules.RuleRepository
import com.ptut.expense.domain.slip.isReaderNoise
import com.ptut.expense.domain.slip.shownShop
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * FR-12 and FR-13. One shop's rule, and the two things that may be done to it.
 *
 * Neither of them touches a transaction. A rule decides where the *next* slip lands, so changing
 * it is a promise about tomorrow rather than an edit to the past — which is why nothing here
 * writes to the ledger and the screen says as much out loud.
 *
 * Keyed by the shop's name and not the rule's id, because the record that sends the reader here
 * carries a counterparty and no rule id. The match is the engine's own — trimmed and lowercased —
 * so a slip that printed the name in a different case still finds the rule it taught.
 */
class ShopRuleViewModel(
    private val shop: String,
    private val rules: RuleRepository,
    private val categories: List<Category> = Category.seed(),
) : ViewModel() {

    private val forgotten = MutableStateFlow(false)

    /** True once the rule is gone, so the screen can leave rather than render a missing one. */
    val isForgotten: StateFlow<Boolean> = forgotten.asStateFlow()

    val uiState: StateFlow<ShopRuleUiState> = rules.rules
        .map { held -> held.matching(shop).state() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ShopRuleUiState.Loading,
        )

    /** The shop keeps its history. Only its next slip lands somewhere new. */
    fun recategorise(categoryId: String) = viewModelScope.launch {
        val rule = rules.rules.value.matching(shop) ?: return@launch
        rules.recategorise(rule.id, categoryId)
    }

    /** A name for a shop read as noise. Its rows show it at once; which slips it files does not change. */
    fun rename(name: String) = viewModelScope.launch {
        val rule = rules.rules.value.matching(shop) ?: return@launch
        rules.rename(rule.id, name)
    }

    /** The past stays filed. The shop is simply asked about again the next time it appears. */
    fun forget() = viewModelScope.launch {
        val rule = rules.rules.value.matching(shop) ?: return@launch
        rules.forget(rule.id)
        forgotten.value = true
    }

    /**
     * No rule is [ShopRuleUiState.Missing] whether it was forgotten from here or never existed.
     * The screen leaves on [isForgotten] rather than on this, so the two do not need telling apart.
     */
    private fun RememberedMerchant?.state(): ShopRuleUiState = this?.let {
        ShopRuleUiState.Loaded(
            shop = shownShop(it.counterparty, AppText.copy.slips.unknownShop),
            chosenCategoryId = it.categoryId,
            // C-2: a slip is money sent, so a shop rule can only ever point at spending.
            categories = categories.forDirection(Direction.Expense),
            filedSoFar = AppText.copy.slips.filedSoFar(it.slipCount),
            nameable = it.counterparty.isReaderNoise(),
            name = it.displayName.orEmpty(),
        )
    } ?: ShopRuleUiState.Missing

    /** The engine's own key, so a rule taught in one case still matches a slip printed in another. */
    private fun List<RememberedMerchant>.matching(shop: String): RememberedMerchant? {
        val key = shop.trim().lowercase()
        return firstOrNull { it.counterparty.trim().lowercase() == key }
    }
}
