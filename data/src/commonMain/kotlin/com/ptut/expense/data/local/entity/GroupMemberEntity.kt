package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** #133: a name inside one group. [personId] ties it to the same [PersonEntity] in other groups. */
@Entity(
    tableName = "group_member",
    foreignKeys = [
        ForeignKey(
            entity = SharedGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId"), Index("personId")],
)
data class GroupMemberEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
    val personId: String?,
    val archived: Boolean,
    val createdAtEpochMillis: Long,
    /** Schema 13 (#190): the account that said "this is me". Null while it is only a name. */
    val claimedByUid: String? = null,
    /** Schema 15 (#199): the PromptPay ID this member typed for their own name, for the QR. */
    val promptPayId: String? = null,
)
