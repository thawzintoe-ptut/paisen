package com.ptut.expense.edit

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.ui.AmountEntry
import com.ptut.expense.ui.TransactionMetaRow

/**
 * What the edit screen draws.
 *
 * [Missing] is a state of its own for the same reason the record screen has one: #52 can delete
 * the row from another screen while this is open, and an edit form over a row that has gone would
 * offer to save a correction with nowhere to land.
 */
sealed interface EditTransactionUiState {

    data object Loading : EditTransactionUiState

    data object Missing : EditTransactionUiState

    /**
     * [entry], [categoryId], [memo] and [occurredAtEpochMillis] are editable. The rest are what the
     * row already says, drawn so the reader can see what they are correcting against.
     */
    data class Loaded(
        /** Typed text rather than a figure: formatting mid-entry moves the digits under the thumb. */
        val entry: AmountEntry,
        /**
         * "Read from the slip as ฿642.50", or null on a row no slip produced.
         *
         * Sourced from the slip's own reading, never from [entry] — after a correction the two
         * differ, and that difference is the only reason the line is on the screen.
         */
        val slipRead: String?,
        val categories: List<Category>,
        val categoryId: String?,
        /** [categoryId]'s own sub-categories, the second chip row (#111). */
        val subCategories: List<SubCategory> = emptyList(),
        val subCategoryId: String? = null,
        val memo: String?,
        /** When the money moved, as typed or as stored. */
        val occurredAtEpochMillis: Long = 0,
        /** MERCHANT and REFERENCE: evidence, not preferences. Read-only, always. */
        val evidence: List<TransactionMetaRow>,
        /** Says which fields stay as read, and that saving marks the row edited in the ledger. */
        val notice: String,
        /**
         * The button doubles as the dirty indicator, so this is a value comparison against the
         * loaded record and never a record of what has been touched. Dark on an empty amount (#214).
         */
        val canSave: Boolean,
        /**
         * #214: a value differs from the record, so leaving asks first. Apart from [canSave]: an
         * emptied amount cannot be saved, yet closing would still lose it.
         */
        val changed: Boolean = canSave,
        /** The note is typed in its own sheet over the form. */
        val editingMemo: Boolean = false,
        /** Titles the form "Edit income" rather than "Edit purchase" (#147, R10). */
        val isIncome: Boolean = false,
        /** #186: "This also changes Hanoi & Saigon" when the purchase is shared in a group. */
        val alsoChanges: String? = null,
    ) : EditTransactionUiState
}
