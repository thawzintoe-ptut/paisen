package com.ptut.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ptut.expense.data.local.entity.ImportedSlipEntity
import com.ptut.expense.data.local.entity.RuleEntity
import com.ptut.expense.data.local.entity.TransactionEntity

/**
 * The one operation that spans four tables: confirming a queued slip writes the transaction,
 * remembers the merchant, records the picture as read, and takes the slip off the queue.
 *
 * It is its own DAO because it is its own transaction. Split across the table DAOs, a
 * failure between two of them would leave a rule for a slip that was never filed, or a queue
 * entry for money already in the ledger — and the reader would have no way to tell which.
 */
@Dao
abstract class SlipConfirmationDao {

    /**
     * Aborts on a clash rather than ignoring. Both keys are ones this app mints, so a collision is
     * a bug — and an IGNORE here would drop the transaction while still clearing the queue row,
     * which reads to the person confirming as their money disappearing.
     */
    @Insert
    protected abstract suspend fun insertTransaction(row: TransactionEntity)

    /**
     * FR-12: one rule per merchant. Replacing rather than ignoring, because the category just
     * chosen is the reader's latest answer — an ignored insert would leave the rule filing
     * somewhere the note on screen promised it would not.
     */
    @Query("DELETE FROM rule WHERE counterparty = :counterparty")
    protected abstract suspend fun clearRuleFor(counterparty: String)

    @Insert
    protected abstract suspend fun insertRule(row: RuleEntity)

    @Query("SELECT slipHash FROM queued_slip WHERE id = :id")
    protected abstract suspend fun hashOf(id: String): String?

    /**
     * Neither platform may delete a picture K PLUS wrote, so the next scan meets this one again,
     * and the hash is the only thing that tells it the reader already answered.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun recordAsRead(row: ImportedSlipEntity)

    @Query("DELETE FROM queued_slip WHERE id = :id")
    protected abstract suspend fun removeFromQueue(id: String)

    /** [rule] is null for a slip that read no shop: there is nothing to remember it by (#113). */
    @Transaction
    open suspend fun confirm(
        transaction: TransactionEntity,
        rule: RuleEntity?,
        queuedSlipId: String,
    ) {
        insertTransaction(transaction)
        if (rule != null) {
            clearRuleFor(rule.counterparty)
            insertRule(rule)
        }
        hashOf(queuedSlipId)?.let { hash ->
            recordAsRead(
                ImportedSlipEntity(
                    hash = hash,
                    importedAtEpochMillis = transaction.occurredAtEpochMillis,
                ),
            )
        }
        removeFromQueue(queuedSlipId)
    }
}
