package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * #218: a payment taken out of a shared group's balances, or put back. The log never deletes a
 * payment, so the latest row for it decides, in the same order every phone sorts the log.
 */
@Entity(tableName = "payment_removal", indices = [Index("groupId"), Index("paymentId")])
data class PaymentRemovalEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val paymentId: String,
    /** True takes the payment out; false puts it back. */
    val removed: Boolean,
    val authorMemberId: String?,
    val createdAtEpochMillis: Long,
)
