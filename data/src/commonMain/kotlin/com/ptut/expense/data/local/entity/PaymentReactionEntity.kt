package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Schema 24 (#95): one reaction per person per payment. It syncs with the group, so the backup
 * file leaves it out as it leaves the other group rows out.
 */
@Entity(
    tableName = "payment_reaction",
    foreignKeys = [
        ForeignKey(
            entity = SharedGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId"), Index("paymentId")],
)
data class PaymentReactionEntity(
    /** `<paymentId>_<authorUid>`, the Firestore document id. */
    @PrimaryKey val id: String,
    val groupId: String,
    val paymentId: String,
    val authorUid: String,
    /** Thanks, WellPlayed or Cheers (D10), or Gone: taken back, waiting to be sent, shown to nobody. */
    val kind: String,
    val createdAtEpochMillis: Long,
)
