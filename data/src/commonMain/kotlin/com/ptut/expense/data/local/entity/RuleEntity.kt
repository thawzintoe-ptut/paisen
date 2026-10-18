package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * FR-12, a remembered merchant. The rule decides where *future* slips land; it never rewrites
 * transactions already filed, so nothing here points back at a row.
 */
@Entity(
    tableName = "rule",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            // The net, not the plan: deleting a category moves its rules first (#219), as it does
            // its transactions. A rule left pointing at a dead category would mean nothing.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // One rule per merchant: a second rule for the same name would make classification
        // depend on row order.
        Index(value = ["counterparty"], unique = true),
        Index("categoryId"),
    ],
)
data class RuleEntity(
    @PrimaryKey val id: String,
    val counterparty: String,
    val categoryId: String,
    val createdAtEpochMillis: Long,
    /**
     * Schema 18: the reader's own name for a shop the slip reader could only read as noise (a Thai
     * name). [counterparty] stays the noise, because that is what every later slip prints.
     */
    val displayName: String? = null,
)
