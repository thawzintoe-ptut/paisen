package com.ptut.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ptut.expense.data.local.entity.CategoryEntity
import com.ptut.expense.data.local.entity.SubCategoryEntity
import com.ptut.expense.domain.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    /** The reader's order first. Ties break on name, which is the whole order until one is moved. */
    @Query("SELECT * FROM category ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    /** FR-22. An imported row naming a category this install does not hold has to be caught. */
    @Query("SELECT id FROM category")
    suspend fun ids(): List<String>

    /**
     * FR-12: the id never changes, so a rename reaches every row already filed under it —
     * including history — without touching one of them.
     */
    @Upsert
    suspend fun upsert(category: CategoryEntity)

    /**
     * One transaction over the whole order, so no frame can observe two rows in the same place.
     */
    @Transaction
    suspend fun reorder(places: List<Pair<String, Int>>) =
        places.forEach { (id, place) -> setSortOrder(id, place) }

    @Query("UPDATE category SET sortOrder = :place WHERE id = :id")
    suspend fun setSortOrder(id: String, place: Int)

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Where the transactions go before their category is deleted. Run first and in the same
     * breath, because the alternative is the foreign key nulling them and a screen full of
     * spending belonging to nothing.
     */
    @Query("UPDATE transactions SET categoryId = :to, subCategoryId = NULL WHERE categoryId = :from")
    suspend fun reassign(from: String, to: String)

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :id")
    suspend fun transactionCount(id: String): Int

    /** #219: the shop rules follow the transactions, so the rule's CASCADE never has one to take. */
    @Query("UPDATE rule SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignRules(from: String, to: String)

    @Query("SELECT COUNT(*) FROM rule WHERE categoryId = :id")
    suspend fun ruleCount(id: String): Int

    /** #219: the seed skips an id listed here, so a deleted built-in stays deleted. */
    @Query("INSERT OR IGNORE INTO deleted_built_in (id) VALUES (:id)")
    suspend fun rememberDeletedBuiltIn(id: String)

    /** FR-18 in one transaction (#116): the rows never move unless their category goes with them. */
    @Transaction
    suspend fun reassignAndDelete(from: String, to: String) {
        reassign(from = from, to = to)
        reassignRules(from = from, to = to)
        if (Category.seed().any { it.id == from }) rememberDeletedBuiltIn(from)
        delete(from)
    }

    @Upsert
    suspend fun upsertSubCategories(rows: List<SubCategoryEntity>)

    @Query("DELETE FROM sub_category WHERE id IN (:ids)")
    suspend fun deleteSubCategories(ids: List<String>)

    /** Edit category's one Save (#116). The category goes first, so a new one exists before its sub-categories. */
    @Transaction
    suspend fun save(category: CategoryEntity, subCategories: List<SubCategoryEntity>, removed: List<String>) {
        upsert(category)
        if (removed.isNotEmpty()) deleteSubCategories(removed)
        if (subCategories.isNotEmpty()) upsertSubCategories(subCategories)
    }
}
