package com.ptut.expense.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SpendByCategoryTest {

    @Test
    fun ranksCategoriesByWhatWasSpentAndGivesEachItsShareOfTheTotal() {
        val spend = listOf(
            expense(30_000, "transport"),
            expense(50_000, "food"),
            expense(20_000, "food"),
        ).spendByCategory(Category.seed())

        assertEquals(listOf("Food & drink", "Transport"), spend.map { it.category.name })
        assertEquals(listOf(Satang(70_000), Satang(30_000)), spend.map { it.total })
        assertEquals(listOf(70, 30), spend.map { it.share })
    }

    @Test
    fun leavesIncomeOutOfASpendingBreakdown() {
        val spend = listOf(
            expense(10_000, "food"),
            Transaction(
                id = "salary",
                occurredAtEpochMillis = 0L,
                amount = Satang(3_200_000),
                direction = Direction.Income,
                categoryId = "other",
                counterparty = "Salary",
            ),
        ).spendByCategory(Category.seed())

        assertEquals(listOf("Food & drink"), spend.map { it.category.name })
    }

    /** #115: spend the list cannot name is not dropped, so the rows still add up to the total. */
    @Test
    fun spendUnderACategoryTheListDoesNotHoldCountsAsUnknown() {
        val spend = listOf(
            expense(80_000, "food"),
            expense(20_000, "added-later"),
        ).spendByCategory(Category.seed())

        assertEquals(listOf("Food & drink", "Unknown category"), spend.map { it.category.name })
        assertEquals(Satang(100_000), spend.map { it.total }.sum())
        assertEquals(100, spend.sumOf { it.share })
    }

    /** p34: the parents stay the main read, and the split under one adds up to it. */
    @Test
    fun splitsAParentByItsSubCategoriesWithTheRestAsUnsorted() {
        val spend = listOf(
            expense(30_000, "groceries", subCategoryId = "supermarket"),
            expense(20_000, "groceries", subCategoryId = "market"),
            expense(10_000, "groceries"),
            expense(5_000, "food"),
        ).spendByCategory(
            Category.seed(),
            listOf(
                SubCategory("supermarket", "groceries", "Supermarket"),
                SubCategory("market", "groceries", "Market"),
            ),
        )

        val groceries = spend.single { it.category.id == "groceries" }
        assertEquals(Satang(60_000), groceries.total)
        assertEquals(listOf("Supermarket", "Market", "Unsorted"), groceries.subCategories.map { it.name })
        assertEquals(listOf(Satang(30_000), Satang(20_000), Satang(10_000)), groceries.subCategories.map { it.total })
        assertEquals(emptyList(), spend.single { it.category.id == "food" }.subCategories)
    }

    private fun expense(satang: Long, categoryId: String, subCategoryId: String? = null) = Transaction(
        id = "$categoryId$satang",
        occurredAtEpochMillis = 0L,
        amount = Satang(satang),
        direction = Direction.Expense,
        categoryId = categoryId,
        subCategoryId = subCategoryId,
        counterparty = null,
    )
}
