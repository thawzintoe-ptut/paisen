package com.ptut.expense.shared

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.domain.shared.QuickSplit
import com.ptut.expense.domain.shared.SplitMode

/** [detail] is #218's line under a Percent or Shares field: the amount, and a dropped decimal. */
data class SplitRowUiModel(val memberId: String, val name: String, val isMe: Boolean, val text: String, val part: String, val detail: String? = null)

data class SplitUiModel(
    val title: String,
    val mode: SplitMode,
    val rows: List<SplitRowUiModel>,
    /** p29's check card, or null when the split adds up. */
    val leftTitle: String?,
    val leftBody: String?,
    val giveToLabel: String?,
    val progress: Float,
    val complete: Boolean,
)

/** One tap in the Paid by and split sheet: who paid and how it splits, with what that means for you. */
data class QuickRowUiModel(
    val payerId: String,
    val choice: QuickSplit,
    val label: String,
    val result: String?,
    /** The result is money you owe, so the view paints it in the owe colour. */
    val owe: Boolean,
    val selected: Boolean,
)

data class PayerChipUiModel(val memberId: String, val name: String, val selected: Boolean)

data class QuickSplitUiModel(
    /** The card's line: the chosen row's words, or payer and mode for a split set by hand. */
    val summary: String,
    /** Empty in a group of two, where the four rows name the payer themselves. */
    val chips: List<PayerChipUiModel>,
    val rows: List<QuickRowUiModel>,
    /** Under More options: the modes behind it, or the split set by hand. */
    val moreHint: String,
    val moreSelected: Boolean,
)

sealed interface AddExpenseUiState {
    data object Missing : AddExpenseUiState

    data class Ready(
        val form: ExpenseForm,
        val isEdit: Boolean,
        val groupLine: String,
        val currencies: List<Currency>,
        val amountShown: String,
        /** "≈ ฿2,560 at ฿1.38 per 1,000 ₫, saved with the expense", or null in the group's own money (#48). */
        val groupMoneyLine: String?,
        val rateUnitLabel: String?,
        val categories: List<Category>,
        val categoryName: String?,
        val categoryIcon: String?,
        val payers: List<SplitRowUiModel>,
        val payerName: String,
        val splitLabel: String,
        val resultLine: String?,
        /** The result line's second half, "you are owed ฿320", which the save toast says once more. */
        val meaning: String? = null,
        val split: SplitUiModel,
        val quick: QuickSplitUiModel,
        val canSave: Boolean,
        /** #186: why the locked fields can't change here, or null when nothing is locked. */
        val lockedLine: String? = null,
        /** #216: what is missing while Save is dark, or null. */
        val hint: String? = null,
        /** #214: something differs from how the screen opened, so leaving asks first. */
        val changed: Boolean = false,
    ) : AddExpenseUiState
}
