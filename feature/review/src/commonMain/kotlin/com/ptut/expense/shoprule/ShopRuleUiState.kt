package com.ptut.expense.shoprule

import com.ptut.expense.domain.Category

/**
 * FR-12's correction. The rules screen went with rev 5 — the shop that triggered a sorting is an
 * implementation detail — but the act of correcting one still has to exist, or a shop filed into
 * the wrong category stays there for good.
 *
 * [Missing] is its own state: a rule can be forgotten from here, and a back stack restored after
 * that still holds the route. A blank screen with a live Forget button would offer to forget
 * something already gone.
 */
sealed interface ShopRuleUiState {

    data object Loading : ShopRuleUiState

    data object Missing : ShopRuleUiState

    data class Loaded(
        val shop: String,
        val chosenCategoryId: String,
        val categories: List<Category>,
        /**
         * How much automatic filing the reader would lose by forgetting this. Counted over the
         * ledger rather than stored, so it cannot drift when a transaction is deleted.
         */
        val filedSoFar: String,
        /** The shop was read as noise, so the reader can give it a name (research finding 2). */
        val nameable: Boolean = false,
        val name: String = "",
    ) : ShopRuleUiState
}
