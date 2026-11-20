package com.ptut.expense.domain.categories

import com.ptut.expense.domain.settings.AppSettings

/** #21: takes the weekly target off only when it is [categoryId]'s; another category's stays. */
class ClearCategoryTargetUseCase(private val settings: AppSettings) {
    operator fun invoke(categoryId: String) {
        if (settings.choices.value.weeklyTarget?.categoryId == categoryId) settings.setWeeklyTarget(null)
    }
}
