package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * #32: a shared expense you paid, tied to your own transaction. Kept on this phone only: other
 * members' phones never see your ledger (#189). Deleting the transaction drops the link.
 */
@Entity(
    tableName = "expense_link",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["transactionId"], unique = true)],
)
data class ExpenseLinkEntity(
    @PrimaryKey val expenseId: String,
    val transactionId: String,
)
