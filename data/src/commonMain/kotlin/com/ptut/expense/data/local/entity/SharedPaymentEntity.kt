package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** p31: a payment between two members, in satang. It never edits an expense. */
@Entity(tableName = "shared_payment", indices = [Index("groupId")])
data class SharedPaymentEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amountSatang: Long,
    val method: String,
    val occurredAtEpochMillis: Long,
    val authorMemberId: String?,
    val createdAtEpochMillis: Long,
)
