package com.ptut.expense.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryTest {

    @Test
    fun spendingIsTheSixTheDesignDraws() {
        assertEquals(
            listOf("Food & drink", "Groceries", "Transport", "Bills", "Shopping", "Other"),
            Category.spending.map { it.name },
        )
    }

    /**
     * FR-14 wants a category on income too and the six above are each a way to spend, so income
     * carries one of its own. The design's ledger row reads "Income · added by hand".
     */
    @Test
    fun incomeCarriesItsOwnCategoryBecauseNoneOfTheSixIsAThingASalaryIs() {
        assertEquals(listOf("Income"), Category.earning.map { it.name })
    }

    /** C-2: a slip is money sent. Offering Income there would file a payment as earnings. */
    @Test
    fun theChoiceOnOfferFollowsTheDirectionOfTheMoney() {
        assertEquals(Category.spending, Category.seed().forDirection(Direction.Expense))
        assertEquals(Category.earning, Category.seed().forDirection(Direction.Income))
    }

    /**
     * The gap this column closes: the split used to be two lists compiled into this file, so a
     * category the reader made was in neither and never reached the sheet that files into it.
     */
    @Test
    fun aCategoryTheReaderMadeIsOfferedLikeAnyOther() {
        val mine = Category("gym", "Gym", "more_horiz")
        val ours = Category("tips", "Tips", "payments", Direction.Income)

        val held = Category.seed() + mine + ours

        assertTrue(mine in held.forDirection(Direction.Expense))
        assertTrue(ours in held.forDirection(Direction.Income))
        assertTrue(mine !in held.forDirection(Direction.Income))
    }

    /** Expense unless it says otherwise: most of what anyone makes is a way to spend. */
    @Test
    fun aCategoryWithNoDirectionGivenIsMoneyOut() {
        assertEquals(Direction.Expense, Category("gym", "Gym", "more_horiz").direction)
    }

    /** The table holds every category a stored row could point at, or the lookup returns null. */
    @Test
    fun theSeedInstallsBothSides() {
        assertEquals(Category.spending + Category.earning, Category.seed())
    }

    @Test
    fun seedIdsAreStableSoRulesSurviveARename() {
        assertEquals(
            listOf("food", "groceries", "transport", "bills", "shopping", "other", "income"),
            Category.seed().map { it.id },
        )
        assertTrue(Category.seed().all { it.icon.isNotBlank() })
    }
}
