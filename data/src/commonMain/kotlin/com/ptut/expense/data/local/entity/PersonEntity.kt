package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** #56: a name on this phone that can belong to no group or many. Archived, never deleted. */
@Entity(tableName = "person")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val archived: Boolean,
)
