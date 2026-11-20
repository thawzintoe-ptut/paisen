package com.ptut.expense.domain.categories

import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.settings.appSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClearCategoryTargetUseCaseTest {

    private val foodTarget = WeeklyTarget("food", Satang.ofMinorUnits(150_000))

    @Test
    fun thisCategorysTargetIsTakenOff() {
        val settings = appSettings(AppChoices(weeklyTarget = foodTarget))

        ClearCategoryTargetUseCase(settings)("food")

        assertNull(settings.choices.value.weeklyTarget)
    }

    @Test
    fun anotherCategorysTargetStays() {
        val settings = appSettings(AppChoices(weeklyTarget = foodTarget))

        ClearCategoryTargetUseCase(settings)("travel")

        assertEquals(foodTarget, settings.choices.value.weeklyTarget)
    }
}
