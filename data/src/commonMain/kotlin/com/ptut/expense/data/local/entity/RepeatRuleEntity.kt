package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Schema 14 (#182): one repeat and the expense it adds. Dates are ISO `yyyy-MM-dd` on Bangkok's
 * calendar. [lastAdded] and [remind] belong to this phone and never sync (#189): every phone
 * catches up on its own, and the fixed entry ids merge what two phones add on the same day.
 */
@Entity(
    tableName = "repeat_rule",
    foreignKeys = [
        ForeignKey(
            entity = SharedGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class RepeatRuleEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val kind: String,
    val frequency: String,
    val startDate: String,
    /** "Never", "OnDate" with [endDate], or "After" with [endTimes]. */
    val endKind: String,
    val endDate: String?,
    val endTimes: Int?,
    val remind: Boolean,
    val active: Boolean,
    val lastAdded: String?,
    val description: String,
    val categoryId: String?,
    val currency: String,
    val amountMinor: Long,
    val rateUnscaled: Long?,
    val rateScale: Int?,
    val payerMemberId: String,
    val splitMode: String,
    /** Member id to weight as `id=weight;id=weight`, in the split mode's own unit. */
    val weights: String,
    val createdAtEpochMillis: Long,
)
