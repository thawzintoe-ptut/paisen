package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * #189: a row of a shared group waiting to reach Firestore. Written beside the change itself and
 * removed once the upload lands, so nothing is lost when the phone is offline.
 */
@Entity(tableName = "sync_outbox", indices = [Index("groupId")])
data class SyncOutboxEntity(
    /** "<kind>:<rowId>", so queuing the same row twice keeps one entry. */
    @PrimaryKey val id: String,
    val groupId: String,
    /** group, member, version or payment. */
    val kind: String,
    val rowId: String,
    val createdAtEpochMillis: Long,
)
