package com.ptut.expense.domain.categories

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SubCategory
import kotlinx.coroutines.flow.StateFlow

/**
 * FR-12 and FR-13: the categories are the reader's, so something has to own changing them.
 *
 * Every edit here is non-destructive by construction. A rename writes one row and history follows
 * it, because a transaction holds a category id and never a name. Removing a sub-category drops a
 * label and no money. Deleting a category is the one edit that could orphan spending, so it is the
 * one that will not run without being told where the spending goes.
 */
interface CategoryRepository {

    val categories: StateFlow<List<Category>>

    val subCategories: StateFlow<List<SubCategory>>

    /**
     * Creates or renames. The id decides which, and it never changes under a rename. The
     * sub-category edits Edit category held until Save land with it in one transaction (#116).
     */
    suspend fun save(
        category: Category,
        subCategories: List<SubCategory> = emptyList(),
        removedSubCategoryIds: List<String> = emptyList(),
    )

    /** Creates or renames, by id. A rename keeps the id, so the spending under it never moves. */
    suspend fun saveSubCategory(subCategory: SubCategory)

    /**
     * Writes the reader's order across [ordered] in one go.
     *
     * A list and not a pair of ids, because a swap of two rows still has to renumber everything
     * around it once a row has been moved twice — and one write of the whole order cannot leave
     * two categories claiming the same place.
     */
    suspend fun reorder(ordered: List<Category>)

    /**
     * FR-13's promise, in one method: the label goes and the spending stays where it was. Every
     * transaction filed under it keeps its amount and its parent category.
     */
    suspend fun removeSubCategory(id: String)

    /** How many rows would be moved by [delete], which the screen states before it asks. */
    suspend fun transactionCount(categoryId: String): Int

    /** How many shop rules [delete] would move with them (#219), stated beside [transactionCount]. */
    suspend fun shopRuleCount(categoryId: String): Int

    /**
     * FR-18. [moveTo] is not optional, and that is the point — a category cannot be deleted
     * without an answer to where its transactions belong. Its shop rules go there too (#219).
     */
    suspend fun delete(categoryId: String, moveTo: String)
}
