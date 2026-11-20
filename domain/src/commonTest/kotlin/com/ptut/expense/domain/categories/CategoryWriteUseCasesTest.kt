package com.ptut.expense.domain.categories

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.WeeklyTarget
import com.ptut.expense.domain.settings.AppChoices
import com.ptut.expense.domain.settings.appSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryWriteUseCasesTest {

    private val food = Category(id = "food", name = "Food & drink", icon = "restaurant")
    private val travelTarget = WeeklyTarget("travel", Satang.ofMinorUnits(100_000))
    private val repository = RecordingCategories()

    @Test
    fun aCategoryIsSavedWithItsSubCategoryEditsTogether() = runTest {
        val sushi = SubCategory("sushi", "food", "Sushi")

        val saved = SaveCategoryUseCase(repository)(food, listOf(sushi), listOf("ramen"), targetText = null)

        assertTrue(saved.isSuccess)
        assertEquals(listOf(Triple(food, listOf(sushi), listOf("ramen"))), repository.saved)
    }

    @Test
    fun aTypedTargetBecomesThisCategorysWeeklyTargetInWholeUnits() = runTest {
        val settings = appSettings(AppChoices(weeklyTarget = travelTarget))

        SaveCategoryUseCase(repository, settings)(food, emptyList(), emptyList(), targetText = "1500")

        assertEquals(WeeklyTarget("food", Satang.ofMinorUnits(150_000)), settings.choices.value.weeklyTarget)
    }

    @Test
    fun anEmptiedTargetTakesOffOnlyThisCategorysOwn() = runTest {
        val others = appSettings(AppChoices(weeklyTarget = travelTarget))
        val own = appSettings(AppChoices(weeklyTarget = WeeklyTarget("food", Satang.ofMinorUnits(150_000))))

        SaveCategoryUseCase(repository, others)(food, emptyList(), emptyList(), targetText = "")
        SaveCategoryUseCase(repository, own)(food, emptyList(), emptyList(), targetText = "0")

        assertEquals(travelTarget, others.choices.value.weeklyTarget)
        assertNull(own.choices.value.weeklyTarget)
    }

    @Test
    fun anUntouchedTargetFieldLeavesTheTargetAlone() = runTest {
        val settings = appSettings(AppChoices(weeklyTarget = WeeklyTarget("food", Satang.ofMinorUnits(150_000))))

        SaveCategoryUseCase(repository, settings)(food, emptyList(), emptyList(), targetText = null)

        assertEquals(WeeklyTarget("food", Satang.ofMinorUnits(150_000)), settings.choices.value.weeklyTarget)
    }

    @Test
    fun aFailedSaveWritesNoTarget() = runTest {
        val settings = appSettings(AppChoices(weeklyTarget = travelTarget))
        repository.fails = true

        val saved = SaveCategoryUseCase(repository, settings)(food, emptyList(), emptyList(), targetText = "1500")

        assertTrue(saved.isFailure)
        assertEquals(travelTarget, settings.choices.value.weeklyTarget)
    }

    @Test
    fun aDeletedCategoryTakesItsTargetWithIt() = runTest {
        val settings = appSettings(AppChoices(weeklyTarget = WeeklyTarget("food", Satang.ofMinorUnits(150_000))))

        assertTrue(DeleteCategoryUseCase(repository, ClearCategoryTargetUseCase(settings))("food", moveTo = "other").isSuccess)

        assertEquals(listOf("food" to "other"), repository.deleted)
        assertNull(settings.choices.value.weeklyTarget)
    }

    @Test
    fun aFailedDeleteKeepsTheTarget() = runTest {
        val target = WeeklyTarget("food", Satang.ofMinorUnits(150_000))
        val settings = appSettings(AppChoices(weeklyTarget = target))
        repository.fails = true

        assertTrue(DeleteCategoryUseCase(repository, ClearCategoryTargetUseCase(settings))("food", moveTo = "other").isFailure)
        assertEquals(target, settings.choices.value.weeklyTarget)
    }

    private class RecordingCategories : CategoryRepository {
        val saved = mutableListOf<Triple<Category, List<SubCategory>, List<String>>>()
        val deleted = mutableListOf<Pair<String, String>>()
        var fails = false

        override val categories: StateFlow<List<Category>> = MutableStateFlow(emptyList())
        override val subCategories: StateFlow<List<SubCategory>> = MutableStateFlow(emptyList())

        override suspend fun save(category: Category, subCategories: List<SubCategory>, removedSubCategoryIds: List<String>) {
            if (fails) error("disk")
            saved += Triple(category, subCategories, removedSubCategoryIds)
        }

        override suspend fun delete(categoryId: String, moveTo: String) {
            if (fails) error("disk")
            deleted += categoryId to moveTo
        }

        override suspend fun saveSubCategory(subCategory: SubCategory) = error("Not expected in this test")

        override suspend fun reorder(ordered: List<Category>) = error("Not expected in this test")

        override suspend fun removeSubCategory(id: String) = error("Not expected in this test")

        override suspend fun transactionCount(categoryId: String): Int = error("Not expected in this test")

        override suspend fun shopRuleCount(categoryId: String): Int = error("Not expected in this test")
    }
}
