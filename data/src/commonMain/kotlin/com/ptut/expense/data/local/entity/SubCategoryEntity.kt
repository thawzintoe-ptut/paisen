package com.ptut.expense.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ptut.expense.domain.SubCategory

/**
 * FR-13: one level below a category and no further.
 *
 * There is no parent column pointing at another sub-category, and that absence is the design:
 * two levels is where filing turns into a chore and people stop doing it, and "coffee versus
 * delivery" earns its keep in a way a third tier never does. A schema that cannot express the
 * third tier is how the rule survives someone later thinking it would be handy.
 *
 * CASCADE, unlike the transaction's SET_NULL: a sub-category is a way of reading one category's
 * spending, so it has nothing left to mean once that category is gone.
 */
@Entity(
    tableName = "sub_category",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class SubCategoryEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
)

fun SubCategoryEntity.toDomain(): SubCategory =
    SubCategory(id = id, categoryId = categoryId, name = name)
