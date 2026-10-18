package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** #133: one member's part of one expense version, in the expense's currency and in satang. */
@Entity(
    tableName = "expense_share",
    primaryKeys = ["versionId", "memberId"],
    foreignKeys = [
        ForeignKey(
            entity = ExpenseVersionEntity::class,
            parentColumns = ["versionId"],
            childColumns = ["versionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("memberId")],
)
data class ExpenseShareEntity(
    val versionId: String,
    val memberId: String,
    val weight: Long,
    val amountMinor: Long,
    val amountSatang: Long,
)
