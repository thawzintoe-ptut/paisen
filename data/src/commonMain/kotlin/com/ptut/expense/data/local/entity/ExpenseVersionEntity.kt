package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * #133: one version of a shared expense. An edit inserts the next [version] and never updates an
 * old row; a removal is a version with [removed] set (D8). Every money column is an INTEGER: the
 * amount in [currency]'s minor units and the same amount in satang at the saved rate.
 *
 * No foreign keys on the member columns: once groups sync (#189), a version can arrive before the
 * member it names, and the row has to be kept rather than refused.
 */
@Entity(
    tableName = "expense_version",
    // Not unique (schema 15, #192): two phones editing offline both write version N+1, and both
    // must be kept. Order is (createdAtEpochMillis, versionId), the same on every phone.
    indices = [Index("groupId"), Index(value = ["expenseId", "version"])],
)
data class ExpenseVersionEntity(
    @PrimaryKey val versionId: String,
    val expenseId: String,
    val groupId: String,
    val version: Int,
    val description: String,
    val occurredAtEpochMillis: Long,
    val categoryId: String?,
    val subCategoryId: String?,
    val note: String?,
    val currency: String,
    val amountMinor: Long,
    /** The saved rate as an exact decimal, unscaled ÷ 10^scale baht per unit. Null for THB. */
    val rateUnscaled: Long?,
    val rateScale: Int?,
    val amountSatang: Long,
    val payerMemberId: String,
    val splitMode: String,
    val authorMemberId: String?,
    val createdAtEpochMillis: Long,
    val removed: Boolean,
    /** Schema 14: why a version was written when nobody typed it, see [com.ptut.expense.domain.shared.ExpenseCause]. */
    val cause: String? = null,
)
