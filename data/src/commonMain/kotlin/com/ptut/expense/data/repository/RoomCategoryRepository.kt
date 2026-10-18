package com.ptut.expense.data.repository

import com.ptut.expense.data.local.dao.CategoryDao
import com.ptut.expense.data.local.dao.SubCategoryDao
import com.ptut.expense.data.local.entity.CategoryEntity
import com.ptut.expense.data.local.entity.SubCategoryEntity
import com.ptut.expense.data.local.entity.toDomain
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.categories.CategoryRepository
import com.ptut.expense.domain.inAppLanguage
import com.ptut.expense.domain.inTableLanguage
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * [CategoryRepository] over Room. Mapping, and one rule the SQL cannot state on its own: the
 * reassignment runs before the delete, in the same transaction.
 */
class RoomCategoryRepository(
    private val categoryDao: CategoryDao,
    private val subCategoryDao: SubCategoryDao,
    scope: CoroutineScope,
) : CategoryRepository {

    // With the language, so an installed category's name changes as soon as the language does.
    override val categories: StateFlow<List<Category>> = combine(categoryDao.observeAll(), AppText.language) { rows, _ ->
        rows.map { it.toDomain().inAppLanguage() }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val subCategories: StateFlow<List<SubCategory>> = subCategoryDao.observeAll()
        .map { rows -> rows.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun save(
        category: Category,
        subCategories: List<SubCategory>,
        removedSubCategoryIds: List<String>,
    ) = categoryDao.save(
        category = CategoryEntity(
            id = category.id,
            name = category.inTableLanguage().name,
            icon = category.icon,
            direction = category.direction.name,
            color = category.color,
            sortOrder = category.sortOrder,
        ),
        subCategories = subCategories.map { it.toEntity() },
        removed = removedSubCategoryIds,
    )

    override suspend fun reorder(ordered: List<Category>) =
        categoryDao.reorder(ordered.mapIndexed { place, category -> category.id to place })

    override suspend fun saveSubCategory(subCategory: SubCategory) = subCategoryDao.upsert(subCategory.toEntity())

    private fun SubCategory.toEntity() = SubCategoryEntity(id = id, categoryId = categoryId, name = name)

    override suspend fun removeSubCategory(id: String) = subCategoryDao.delete(id)

    override suspend fun transactionCount(categoryId: String): Int =
        categoryDao.transactionCount(categoryId)

    override suspend fun shopRuleCount(categoryId: String): Int = categoryDao.ruleCount(categoryId)

    override suspend fun delete(categoryId: String, moveTo: String) {
        require(categoryId != moveTo) { "A category cannot be the place its own transactions go" }

        // Before the delete, never after: the foreign key would null the category on every row
        // and take its shop rules, and spending belonging to nothing is what FR-18 exists to prevent.
        categoryDao.reassignAndDelete(from = categoryId, to = moveTo)
    }
}
