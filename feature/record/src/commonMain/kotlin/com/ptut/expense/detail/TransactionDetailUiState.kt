package com.ptut.expense.detail

import com.ptut.expense.domain.MoneyParts
import com.ptut.expense.ui.TransactionMetaRow

/**
 * What the transaction detail screen draws.
 *
 * [Missing] is a state of its own rather than a [Loaded] with empty fields, because the row can
 * genuinely go: #52 deletes one, and a back stack restored afterwards still holds its id. A blank
 * record under a live Delete button would offer to delete something that is already gone.
 */
/** What the record says about the rule that filed it, and where tapping it goes. */
data class ShopRuleRowUiModel(val shop: String, val filesUnder: String, val why: String = "")

/** #186: a group this purchase can be shared into, for the picker behind "Share this cost". */
data class ShareGroupUiModel(val id: String, val name: String, val detail: String)

sealed interface TransactionDetailUiState {

    data object Loading : TransactionDetailUiState

    data object Missing : TransactionDetailUiState

    /**
     * No field here is editable. FR-21 gives the record a whole screen precisely so the reader
     * looks at it before the one action it leads to; a field that opened a picker would make this
     * the edit screen (#53) by accident.
     *
     * [shopRule] is the one destination, and it is not a field of the record: it is a promise
     * about the shop's *next* slip, which is why correcting it happens on a screen of its own
     * rather than under the reader's thumb here.
     */
    data class Loaded(
        val merchant: String,
        val amount: MoneyParts,
        /** The card's one description. Without it TalkBack reads the amount as three figures. */
        val spoken: String,
        val categoryIcon: String,
        /** The category's tint, so the record wears the colour its ledger row does. */
        val categoryColor: String,
        val rows: List<TransactionMetaRow>,
        /** The note on the screen. NFR-4's warning about the slip image lives here. */
        val consequence: String,
        /**
         * The delete sheet's own sentence, the money and the totals it moves: "฿642.50 comes off
         * this month." Deliberately not the note's: the slip image is already said behind it. The
         * sheet adds the undo window itself, read off the motion token rather than typed (#162).
         */
        val deleteMoves: String,
        /** Titles the record "Income" rather than "Purchase" (#147, R10). */
        val isIncome: Boolean = false,
        val ruleFooter: String,
        /** Null where no rule files this shop — a row added by hand, or a shop never remembered. */
        val shopRule: ShopRuleRowUiModel? = null,
        /**
         * FR-21's dialog is state rather than something the view remembers, so the answer to
         * "did they mean it" survives a rotation the way the record above it does.
         */
        val confirmingDelete: Boolean = false,
        /** #186: "Share this cost" shows when this is a purchase not shared yet, in a build with v2. */
        val canShare: Boolean = false,
        val shareGroups: List<ShareGroupUiModel> = emptyList(),
        /** #186: the group this purchase is shared in, for "Shared in Hanoi & Saigon >". */
        val sharedGroupId: String? = null,
        val sharedGroupName: String? = null,
        /** #186: the delete sheet's "Also remove it from …" box, ticked unless the reader unticks it. */
        val alsoRemoveFromGroup: Boolean = true,
    ) : TransactionDetailUiState
}
