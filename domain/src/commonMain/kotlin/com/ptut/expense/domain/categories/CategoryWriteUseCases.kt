package com.ptut.expense.domain.categories

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.minorPerWhole
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.settings.AppSettings

/**
 * #116: the category and its sub-category edits in one transaction, then the weekly target (#21) from [targetText]
 * (null when untouched; zero clears only this category's). A failed save writes no target, so Save can be tapped again.
 */
class SaveCategoryUseCase(
    private val categories: CategoryRepository,
    private val settings: AppSettings? = null,
) {
    private val clearTarget = settings?.let(::ClearCategoryTargetUseCase)

    suspend operator fun invoke(
        category: Category,
        subCategories: List<SubCategory>,
        removedSubCategoryIds: List<String>,
        targetText: String?,
    ): Result<Unit> = runSuspendCatching {
        categories.save(category, subCategories = subCategories, removedSubCategoryIds = removedSubCategoryIds)
    }.onSuccess {
        val store = settings ?: return@onSuccess
        val typed = targetText ?: return@onSuccess
        val wholeUnits = typed.toLongOrNull()
        if (wholeUnits != null && wholeUnits > 0) {
            store.setWeeklyTarget(WeeklyTarget(category.id, Satang.ofMinorUnits(wholeUnits * minorPerWhole())))
        } else {
            clearTarget?.invoke(category.id)
        }
    }
}

/** FR-18: the category goes and its rows move to [moveTo]; #21: its weekly target goes with it. */
class DeleteCategoryUseCase(
    private val categories: CategoryRepository,
    private val clearTarget: ClearCategoryTargetUseCase? = null,
) {
    suspend operator fun invoke(categoryId: String, moveTo: String): Result<Unit> =
        runSuspendCatching { categories.delete(categoryId, moveTo) }.onSuccess { clearTarget?.invoke(categoryId) }
}
