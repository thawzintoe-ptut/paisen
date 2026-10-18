package com.ptut.expense.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.DEFAULT_COLOR
import com.ptut.expense.domain.Direction

/**
 * FR-12: [id] is stable and never derived from [name], so a rename cannot orphan a rule.
 *
 * [direction] carries a default so schema 7 migrates on its own. Expense is right for every row
 * already in the table bar one — the seed's own `income`, which
 * [com.ptut.expense.data.local.IncomeKeepsItsDirection] corrects as part of the migration. The
 * seed cannot: it inserts or ignores, so on an upgrade it would step over the row it needs to fix.
 */
@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    @ColumnInfo(defaultValue = "Expense")
    val direction: String = Direction.Expense.name,
    /** A tint name. Defaults to the theme primary, which is what every row wore before schema 8. */
    @ColumnInfo(defaultValue = DEFAULT_COLOR)
    val color: String = DEFAULT_COLOR,
    /**
     * The reader's own order. Every row already in the table lands on 0 and so keeps sorting by
     * name, which is the order they were being shown in before the column existed.
     */
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    icon = icon,
    // A value the enum does not know is a row from a build that wrote something else. Money out is
    // the safe read: it offers the category on the sheet most spending goes through.
    direction = Direction.entries.firstOrNull { it.name == direction } ?: Direction.Expense,
    color = color,
    sortOrder = sortOrder,
)
