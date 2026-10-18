package com.ptut.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ptut.expense.data.local.entity.CategoryEntity
import com.ptut.expense.data.local.entity.RuleEntity
import com.ptut.expense.data.local.entity.SubCategoryEntity

/**
 * FR-22 format 3 (#215): the categories, sub-categories and shop rules a backup carries.
 *
 * IGNORE, as [LedgerMergeDao] does: a category id this phone already holds stays as the phone has
 * it, and a shop rule for a shop this phone already knows keeps this phone's answer.
 */
@Dao
abstract class CatalogBackupDao {

    @Query("SELECT * FROM category")
    abstract suspend fun categories(): List<CategoryEntity>

    @Query("SELECT * FROM sub_category")
    abstract suspend fun subCategories(): List<SubCategoryEntity>

    @Query("SELECT * FROM rule")
    abstract suspend fun rules(): List<RuleEntity>

    @Query("SELECT id FROM category")
    protected abstract suspend fun categoryIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertCategories(rows: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSubCategories(rows: List<SubCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRules(rows: List<RuleEntity>)

    /**
     * Categories first, so a sub-category or a rule finds its parent. One whose parent is still
     * missing is left out rather than failing the whole file on a foreign key.
     *
     * @return the category ids this phone holds afterwards.
     */
    @Transaction
    open suspend fun restore(
        categories: List<CategoryEntity>,
        subCategories: List<SubCategoryEntity>,
        rules: List<RuleEntity>,
    ): Set<String> {
        insertCategories(categories)
        val known = categoryIds().toSet()
        insertSubCategories(subCategories.filter { it.categoryId in known })
        insertRules(rules.filter { it.categoryId in known })
        return known
    }
}
