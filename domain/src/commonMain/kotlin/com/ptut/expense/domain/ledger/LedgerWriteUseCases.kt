package com.ptut.expense.domain.ledger

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Transaction
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.asEdit
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.TitleChange
import com.ptut.expense.domain.shared.groupHolding
import com.ptut.expense.domain.shared.sharedExpenseOf

/** #33: the rate a currency was last saved at, or null with none saved or no book in this build. */
class GetLastRateUseCase(private val rates: RateBook? = null) {
    suspend operator fun invoke(currency: String): Rate? = rates?.lastRate(currency)
}

/**
 * FR-14: a typed row; a foreign amount's rate then becomes the currency's last rate, and a failed rate save costs only
 * the next guess. A row that fails to write throws, since every reason is a bug or a disk failure.
 */
class RecordTransactionUseCase(
    private val ledger: LedgerRepository,
    private val rates: RateBook? = null,
) {
    suspend operator fun invoke(draft: TransactionDraft) {
        ledger.record(draft)
        draft.foreign?.let { foreign -> runSuspendCatching { rates?.saveRate(foreign.currency, foreign.rate) } }
    }
}

/**
 * A corrected row. O4: the change flows one way, from History to its shared copy; the edit is
 * already durable, so a copy that cannot follow now does not undo it.
 */
class EditTransactionUseCase(
    private val ledger: LedgerRepository,
    private val shared: SharedRepository? = null,
) {
    /** [categories] in the table's language, so a shared copy named after its old category takes the new one's name. */
    suspend operator fun invoke(record: Transaction, edit: TransactionEdit, categories: List<Category>) {
        ledger.applyEdit(record.id, edit)
        runSuspendCatching { shared?.followTransaction(record.id, before = record.asEdit(), titleChange = titleChange(record.categoryId, edit.categoryId, categories)) }
    }

    /** The old and new category names, for a shared copy whose title was only the old name. Null when the category stayed. */
    private fun titleChange(before: String?, after: String?, categories: List<Category>): TitleChange? {
        if (before == after) return null
        val from = categories.firstOrNull { it.id == before }?.name ?: return null
        val to = categories.firstOrNull { it.id == after }?.name ?: return null
        return TitleChange(from, to)
    }
}

/** What a delete did, so its Undo can put back exactly that: the row, and the group copy and its link. */
data class DeleteReceipt(
    val row: Transaction,
    /** The shared expense this row was, or null when it was not shared. */
    val sharedExpenseId: String?,
    /** True when the group's copy went too. */
    val removedShared: Boolean,
    val groupName: String?,
)

/**
 * FR-21: deletes now and offers the row back. #186: [alsoRemoveShared] removes the group's copy too, else only the
 * link goes; a copy that will not go does not fail the delete, since the row is already gone.
 */
class DeleteTransactionUseCase(
    private val ledger: LedgerRepository,
    private val shared: SharedRepository? = null,
) {
    suspend operator fun invoke(row: Transaction, alsoRemoveShared: Boolean): DeleteReceipt {
        // One read, so the expense and its group come from the same snapshot.
        val groups = shared?.snapshot?.value
        val expense = groups?.sharedExpenseOf(row.id)
        val group = groups?.groupHolding(row.id)
        ledger.delete(row.id)
        val removed = shared != null && expense != null && alsoRemoveShared && runSuspendCatching { shared.removeExpense(expense.id) }.isSuccess
        return DeleteReceipt(row, expense?.id, removed, group?.name)
    }
}

/** Undo of [DeleteTransactionUseCase]: the row comes back, then the group copy it removed and the link. */
class UndoDeleteTransactionUseCase(
    private val ledger: LedgerRepository,
    private val shared: SharedRepository? = null,
) {
    suspend operator fun invoke(receipt: DeleteReceipt) {
        ledger.restore(receipt.row)
        val expenseId = receipt.sharedExpenseId ?: return
        if (receipt.removedShared) shared?.restoreExpense(expenseId)
        shared?.relink(expenseId, receipt.row.id)
    }
}
