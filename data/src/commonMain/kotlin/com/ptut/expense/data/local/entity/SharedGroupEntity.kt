package com.ptut.expense.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * #133: a v2 group. [meMemberId] says which member this phone is, and stays on the phone when
 * groups start to sync (#189): every member's phone answers it differently.
 */
@Entity(tableName = "shared_group")
data class SharedGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val icon: String,
    val simplifyDebts: Boolean,
    val createdAtEpochMillis: Long,
    val meMemberId: String?,
    /** Schema 13 (#189): synced with Firestore. False until the group is first shared. */
    @ColumnInfo(defaultValue = "0") val remote: Boolean = false,
    /** The account that shared it first; only it can change the invite (#190). */
    val creatorUid: String? = null,
    val inviteCode: String? = null,
    @ColumnInfo(defaultValue = "0") val inviteActive: Boolean = false,
    val lastSyncedAtEpochMillis: Long? = null,
    /** Schema 18: when this phone last opened the group, for the dot on rows others added since. Never synced. */
    val lastSeenAtEpochMillis: Long? = null,
)
