package com.ptut.expense.domain.categories

import com.ptut.expense.domain.runSuspendCatching

/**
 * Moves one category [by] places and writes the whole order, since seeded categories can share a sortOrder and
 * swapping two zeroes changes nothing. A move past either end does nothing.
 */
class MoveCategoryUseCase(private val categories: CategoryRepository) {
    suspend operator fun invoke(categoryId: String, by: Int): Result<Unit> {
        val ordered = categories.categories.value.sortedWith(readersOrder)
        val from = ordered.indexOfFirst { it.id == categoryId }
        val to = from + by
        if (from < 0 || to !in ordered.indices) return Result.success(Unit)
        return runSuspendCatching { categories.reorder(ordered.toMutableList().apply { add(to, removeAt(from)) }) }
    }
}
