package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * #219: a built-in category the reader deleted. The seed runs on every open, so without this row
 * the category would come back at the next cold start.
 */
@Entity(tableName = "deleted_built_in")
data class DeletedBuiltInEntity(@PrimaryKey val id: String)
