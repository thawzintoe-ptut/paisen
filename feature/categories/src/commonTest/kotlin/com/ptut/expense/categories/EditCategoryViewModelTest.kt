package com.ptut.expense.categories

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.SubCategory
import com.ptut.expense.domain.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Screen 12a. Editing is non-destructive throughout, and the two edits that could lose something
 * say what they will do before they are tapped.
 */
class EditCategoryViewModelTest {

    private val now = Instant.fromEpochMilliseconds(1_786_800_000_000)

    private val food = Category("food", "Food & drink", "restaurant")
    private val groceries = Category("groceries", "Groceries", "local_grocery_store")

    @BeforeTest
    fun pinTheDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun releaseTheDispatcher() = Dispatchers.resetMain()

    @Test
    fun `save is inert until something changes`() = runTest {
        val model = viewModel()
        runCurrent()

        assertFalse(model.uiState.value.dirty)

        model.rename("Eating")
        runCurrent()
        assertTrue(model.uiState.value.dirty)
    }

    @Test
    fun `typing the stored name back is not a change`() = runTest {
        val model = viewModel()
        runCurrent()

        model.rename("Food & drink")
        runCurrent()

        assertFalse(model.uiState.value.dirty)
    }

    @Test
    fun `choosing a direction marks the screen dirty and saves it`() = runTest {
        val categories = FakeCategories(listOf(food, groceries))
        val model = watching(EditCategoryViewModel("food", StaticLedger(emptyList()), categories))
        runCurrent()

        assertFalse(model.uiState.value.dirty)

        model.chooseDirection(Direction.Income)
        runCurrent()
        assertTrue(model.uiState.value.dirty)

        model.save()
        runCurrent()
        assertEquals(Direction.Income, categories.categories.value.single { it.id == "food" }.direction)
    }

    /** The screen reads this before it draws a glyph, so it has to be false until there is one. */
    @Test
    fun `the state says nothing has been read until the category arrives`() = runTest {
        val model = viewModel()

        assertFalse(model.uiState.value.loaded)
        assertEquals("", model.uiState.value.icon)

        runCurrent()
        assertTrue(model.uiState.value.loaded)
        assertEquals("restaurant", model.uiState.value.icon)
    }

    /** One frame after a delete the row is gone, and the screen must not ask for its glyph. */
    @Test
    fun `a category that is not there reads as unread rather than as blank`() = runTest {
        val model = watching(
            EditCategoryViewModel("gone", StaticLedger(emptyList()), FakeCategories(listOf(food))),
        )
        runCurrent()

        assertFalse(model.uiState.value.loaded)
    }

    @Test
    fun `choosing an icon marks the screen dirty and saves it`() = runTest {
        val categories = FakeCategories(listOf(food, groceries))
        val model = watching(EditCategoryViewModel("food", StaticLedger(emptyList()), categories))
        runCurrent()

        assertFalse(model.uiState.value.dirty)

        model.chooseIcon("store")
        runCurrent()
        assertTrue(model.uiState.value.dirty)
        // Shown at once, so the tile answers the tap before Save is anywhere near it.
        assertEquals("store", model.uiState.value.icon)

        model.save()
        runCurrent()
        assertEquals("store", categories.categories.value.single { it.id == "food" }.icon)
    }

    /** FR-12: the id is what history holds, so a new picture cannot move a single transaction. */
    @Test
    fun `re-iconing keeps the name and the id it was found under`() = runTest {
        val categories = FakeCategories(listOf(food, groceries))
        val model = watching(EditCategoryViewModel("food", StaticLedger(emptyList()), categories))
        runCurrent()

        model.chooseIcon("store")
        runCurrent()
        model.save()
        runCurrent()

        val saved = categories.categories.value.single { it.id == "food" }
        assertEquals("Food & drink", saved.name)
        assertEquals(Direction.Expense, saved.direction)
    }

    @Test
    fun `choosing the stored icon back is not a change`() = runTest {
        val model = viewModel()
        runCurrent()

        model.chooseIcon("restaurant")
        runCurrent()

        assertFalse(model.uiState.value.dirty)
    }

    @Test
    fun `choosing the stored direction back is not a change`() = runTest {
        val model = viewModel()
        runCurrent()

        model.chooseDirection(Direction.Expense)
        runCurrent()

        assertFalse(model.uiState.value.dirty)
    }

    @Test
    fun `flipping a category's direction leaves what is already filed there alone`() = runTest {
        val categories = FakeCategories(listOf(food, groceries))
        val filed = listOf(spend("food", "coffee", 64_000))
        val model = watching(EditCategoryViewModel("food", StaticLedger(filed), categories))
        runCurrent()

        model.chooseDirection(Direction.Income)
        runCurrent()
        model.save()
        runCurrent()

        // The row keeps its own direction and its own amount. Only the chips on offer change.
        assertEquals(Direction.Expense, filed.single().direction)
        assertEquals(Satang.ofMinorUnits(64_000), filed.single().amount)
    }

    @Test
    fun `a rename writes the category and keeps its id`() = runTest {
        val categories = FakeCategories(listOf(food, groceries))
        val model = watching(EditCategoryViewModel("food", StaticLedger(emptyList()), categories))
        runCurrent()

        model.rename("Eating")
        runCurrent()
        model.save()
        runCurrent()

        val saved = categories.categories.value.single { it.id == "food" }
        assertEquals("Eating", saved.name)
        assertEquals("restaurant", saved.icon)
    }

    /** p20: how many purchases it holds, all time, is the honest test of whether it earns its place. */
    @Test
    fun `each sub-category shows how many purchases it holds`() = runTest {
        val lastQuarter = spend("food", "eat", 50_000).copy(
            occurredAtEpochMillis = now.toEpochMilliseconds() - 90L * 86_400_000L,
        )
        val model = watching(
            EditCategoryViewModel(
                categoryId = "food",
                ledger = StaticLedger(
                    listOf(spend("food", "coffee", 64_000), spend("food", "eat", 198_000), lastQuarter),
                ),
                categories = FakeCategories(
                    listOf(food),
                    listOf(SubCategory("eat", "food", "Eating out"), SubCategory("coffee", "food", "Coffee")),
                ),
            ),
        )
        runCurrent()

        val byName = model.uiState.value.subCategories.associateBy { it.name }
        assertEquals("2 purchases", byName.getValue("Eating out").purchases)
        assertEquals("1 purchase", byName.getValue("Coffee").purchases)
    }

    @Test
    fun `a sub-category nobody uses shows zero and that is the point`() = runTest {
        val model = watching(
            EditCategoryViewModel(
                categoryId = "food",
                ledger = StaticLedger(emptyList()),
                categories = FakeCategories(listOf(food), listOf(SubCategory("drinks", "food", "Drinks"))),
            ),
        )
        runCurrent()

        assertEquals("0 purchases", model.uiState.value.subCategories.single().purchases)
    }

    @Test
    fun `removing a sub-category drops the label and no money`() = runTest {
        val categories = FakeCategories(listOf(food), listOf(SubCategory("coffee", "food", "Coffee")))
        val spending = listOf(spend("food", "coffee", 64_000))
        val model = watching(EditCategoryViewModel("food", StaticLedger(spending), categories))
        runCurrent()

        model.removeSubCategory("coffee")
        runCurrent()
        assertEquals(1, categories.subCategories.value.size, "removed before Save")

        model.save()
        runCurrent()

        assertTrue(categories.subCategories.value.isEmpty())
        // The transaction is untouched: FR-13 keeps the spending in the parent.
        assertEquals(Satang.ofMinorUnits(64_000), spending.single().amount)
        assertEquals("food", spending.single().categoryId)
    }

    @Test
    fun `a new category is stored on Save after the ones already there`() = runTest {
        val categories = FakeCategories(listOf(food, groceries.copy(sortOrder = 3)))
        val model = watching(EditCategoryViewModel("fresh", StaticLedger(emptyList()), categories, isNew = true))
        runCurrent()
        assertEquals(2, categories.categories.value.size, "stored before Save")

        model.save()
        runCurrent()

        val created = categories.categories.value.single { it.id == "fresh" }
        assertEquals("New category", created.name)
        assertEquals(4, created.sortOrder)
    }

    @Test
    fun `deleting states how many transactions have to move`() = runTest {
        val model = watching(
            EditCategoryViewModel(
                categoryId = "food",
                ledger = StaticLedger(emptyList()),
                categories = FakeCategories(listOf(food, groceries), counts = mapOf("food" to 47)),
            ),
        )
        runCurrent()

        assertEquals(
            "You will be asked where its 47 transactions should go.",
            model.uiState.value.deleteConsequence,
        )
    }

    @Test
    fun `picking a colour lights save and is written with the name and the icon`() = runTest {
        val held = FakeCategories(listOf(food, groceries))
        val model = watching(
            EditCategoryViewModel(
                categoryId = "food",
                ledger = StaticLedger(emptyList()),
                categories = held,
            ),
        )
        runCurrent()
        assertFalse(model.uiState.value.dirty)

        model.chooseColor("rose")
        runCurrent()
        assertEquals("rose", model.uiState.value.color)
        assertTrue(model.uiState.value.dirty)

        model.save()
        runCurrent()

        val stored = held.categories.value.first { it.id == "food" }
        assertEquals("rose", stored.color)
        assertEquals("Food & drink", stored.name)
        assertFalse(model.uiState.value.dirty)
    }

    /**
     * FR-13 again. Delete-and-retype would mint a new id, and every transaction filed under the
     * old label would stop being counted under the new one.
     */
    @Test
    fun `renaming a sub-category keeps its id so the spending under it does not move`() = runTest {
        val coffee = SubCategory(id = "coffee", categoryId = "food", name = "Coffee")
        val held = FakeCategories(listOf(food, groceries), subs = listOf(coffee))
        val model = watching(
            EditCategoryViewModel(
                categoryId = "food",
                ledger = StaticLedger(emptyList()),
                categories = held,
            ),
        )
        runCurrent()

        model.renameSubCategory("coffee", "  Coffee and cake  ")
        runCurrent()
        assertEquals("Coffee", held.subCategories.value.single().name, "renamed before Save")

        model.save()
        runCurrent()

        val stored = held.subCategories.value.single()
        assertEquals("coffee", stored.id)
        assertEquals("Coffee and cake", stored.name)
        assertEquals("food", stored.categoryId)
    }

    @Test
    fun `a sub-category cleared to nothing is not written`() = runTest {
        val coffee = SubCategory(id = "coffee", categoryId = "food", name = "Coffee")
        val held = FakeCategories(listOf(food, groceries), subs = listOf(coffee))
        val model = watching(
            EditCategoryViewModel(
                categoryId = "food",
                ledger = StaticLedger(emptyList()),
                categories = held,
            ),
        )
        runCurrent()

        model.renameSubCategory("coffee", "   ")
        runCurrent()

        assertEquals("Coffee", held.subCategories.value.single().name)
    }

    /**
     * The count is a suspend query and the first frame beats it, so a screen that read it once
     * promised "nothing moves" over a category holding forty-seven rows and never took it back.
     */
    @Test
    fun `the delete line corrects itself when the count lands after the screen`() = runTest {
        val model = watching(
            EditCategoryViewModel(
                categoryId = "food",
                ledger = StaticLedger(emptyList()),
                categories = FakeCategories(
                    listOf(food, groceries),
                    counts = mapOf("food" to 47),
                    countDelay = 50.milliseconds,
                ),
            ),
        )
        runCurrent()
        advanceUntilIdle()

        assertEquals(
            "You will be asked where its 47 transactions should go.",
            model.uiState.value.deleteConsequence,
        )
    }

    /** Save falls back to the stored name, so a lit button here would report a write never made. */
    @Test
    fun `clearing the name does not light save`() = runTest {
        val model = viewModel()
        runCurrent()

        model.rename("")
        runCurrent()
        assertFalse(model.uiState.value.dirty)

        model.rename("   ")
        runCurrent()
        assertFalse(model.uiState.value.dirty)
    }

    @Test
    fun `a category is never offered as the place its own transactions go`() = runTest {
        val model = viewModel()
        runCurrent()

        assertEquals(listOf("groceries"), model.uiState.value.deleteDestinations.map { it.id })
    }

    @Test
    fun `the last category standing cannot be deleted`() = runTest {
        val model = watching(
            EditCategoryViewModel("food", StaticLedger(emptyList()), FakeCategories(listOf(food))),
        )
        runCurrent()

        assertFalse(model.uiState.value.canDelete)
    }

    @Test
    fun `deleting moves the transactions before the category goes`() = runTest {
        val categories = FakeCategories(listOf(food, groceries), counts = mapOf("food" to 47))
        val model = watching(EditCategoryViewModel("food", StaticLedger(emptyList()), categories))
        runCurrent()

        model.delete(moveTo = "groceries")
        runCurrent()

        assertEquals("food" to "groceries", categories.movedTo)
        assertTrue(categories.categories.value.none { it.id == "food" })
        assertTrue(model.isDeleted.value)
    }

    private fun TestScope.viewModel() = watching(
        EditCategoryViewModel(
            categoryId = "food",
            ledger = StaticLedger(emptyList()),
            categories = FakeCategories(listOf(food, groceries)),
        ),
    )

    private fun TestScope.watching(model: EditCategoryViewModel): EditCategoryViewModel {
        backgroundScope.launch { model.uiState.collect {} }
        return model
    }

    private fun spend(categoryId: String, subCategoryId: String, minor: Long) = Transaction(
        id = "$subCategoryId-$minor",
        occurredAtEpochMillis = now.toEpochMilliseconds() - 86_400_000L,
        amount = Satang.ofMinorUnits(minor),
        direction = Direction.Expense,
        categoryId = categoryId,
        subCategoryId = subCategoryId,
        counterparty = null,
    )
}
