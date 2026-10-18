package com.ptut.expense.categories

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.categories.CategoryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * An in-memory [CategoryRepository] that keeps the one rule the SQL keeps: transactions are moved
 * before their category goes, so a delete can never leave spending behind.
 */
class FakeCategories(
    seed: List<Category> = emptyList(),
    subs: List<SubCategory> = emptyList(),
    /** What the ledger would say is filed here, so the screen can state it before it asks. */
    private val counts: Map<String, Int> = emptyMap(),
    /**
     * How long the count takes to come back. Room answers it with a suspend query that loses the
     * race against the screen's first frame, and a fake that answers instantly cannot show that.
     */
    private val countDelay: Duration = Duration.ZERO,
) : CategoryRepository {

    private val known = MutableStateFlow(seed)
    private val finer = MutableStateFlow(subs)

    /** Recorded so a test can assert the transactions went somewhere rather than nowhere. */
    var movedTo: Pair<String, String>? = null
        private set

    override val categories: StateFlow<List<Category>> = known
    override val subCategories: StateFlow<List<SubCategory>> = finer

    override suspend fun save(category: Category, subCategories: List<SubCategory>, removedSubCategoryIds: List<String>) {
        known.value = known.value.filterNot { it.id == category.id } + category
        val changed = subCategories.map { it.id }.toSet() + removedSubCategoryIds
        finer.value = finer.value.filterNot { it.id in changed } + subCategories
    }

    override suspend fun reorder(ordered: List<Category>) {
        val places = ordered.withIndex().associate { (place, it) -> it.id to place }
        known.value = known.value.map { it.copy(sortOrder = places[it.id] ?: it.sortOrder) }
    }

    override suspend fun saveSubCategory(subCategory: SubCategory) {
        finer.value = finer.value.filterNot { it.id == subCategory.id } + subCategory
    }

    override suspend fun removeSubCategory(id: String) {
        finer.value = finer.value.filterNot { it.id == id }
    }

    override suspend fun transactionCount(categoryId: String): Int {
        delay(countDelay)
        return counts[categoryId] ?: 0
    }

    override suspend fun shopRuleCount(categoryId: String): Int = 0

    override suspend fun delete(categoryId: String, moveTo: String) {
        require(categoryId != moveTo) { "A category cannot be the place its own transactions go" }
        movedTo = categoryId to moveTo
        known.value = known.value.filterNot { it.id == categoryId }
        finer.value = finer.value.filterNot { it.categoryId == categoryId }
    }
}
