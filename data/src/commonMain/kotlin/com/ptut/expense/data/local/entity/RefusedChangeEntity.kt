package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * #194: a change the group refused for good. The change itself left this phone so its balances
 * match everyone's; this row keeps what it was, so the reader can enter it again after a restart.
 * [title] is blank for a change that is not an expense, and [amountMinor] null when there was none.
 */
@Entity(tableName = "refused_change", indices = [Index("groupId")])
data class RefusedChangeEntity(
    /** The outbox id it had ("version:<id>"…), so the same refusal is kept once. */
    @PrimaryKey val id: String,
    val groupId: String,
    val title: String,
    val amountMinor: Long?,
    val currency: String?,
    val refusedAtEpochMillis: Long,
)
