package com.ptut.expense.domain.categories

import com.ptut.expense.domain.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CategoryChipsTest {

    private val spending = Category.spending
    private val usage = mapOf("transport" to 9, "bills" to 4, "food" to 2)

    @Test
    fun `the chosen category leads and the two most used follow`() {
        val chips = spending.chips(first = spending.byId("shopping"), usage = usage)

        assertEquals(listOf("shopping", "transport", "bills"), chips.shown.map { it.id })
        assertEquals(3, chips.moreCount)
    }

    @Test
    fun `with nothing chosen the three most used lead`() {
        val chips = spending.chips(first = null, usage = usage)

        assertEquals(listOf("transport", "bills", "food"), chips.shown.map { it.id })
    }

    @Test
    fun `a chosen category that is also most used is not shown twice`() {
        val chips = spending.chips(first = spending.byId("transport"), usage = usage)

        assertEquals(listOf("transport", "bills", "food"), chips.shown.map { it.id })
        assertEquals(3, chips.moreCount)
    }

    @Test
    fun `ties keep the reader's own order`() {
        val chips = spending.chips(first = null, usage = emptyMap())

        assertEquals(listOf("food", "groceries", "transport"), chips.shown.map { it.id })
    }

    @Test
    fun `three or fewer categories are drawn whole with no More`() {
        val three = spending.take(3)

        val chips = three.chips(first = three.last(), usage = usage)

        assertEquals(three, chips.shown)
        assertFalse(chips.hasMore)
    }

    @Test
    fun `a chosen category not on offer is ignored`() {
        val chips = spending.chips(first = Category.earning.single(), usage = usage)

        assertEquals(listOf("transport", "bills", "food"), chips.shown.map { it.id })
    }

    @Test
    fun `tapping a shown chip keeps the chip order`() {
        val shown = listOf("transport", "bills", "food")
        val chips = spending.chips(first = spending.byId("food"), usage = usage)

        val steady = chips.keepingOrder(shown, spending, selectedId = "food")

        assertEquals(shown, steady.shown.map { it.id })
        assertEquals(3, steady.moreCount)
    }

    @Test
    fun `a More pick not shown takes the last chip place`() {
        val chips = spending.chips(first = spending.byId("shopping"), usage = usage)

        val steady = chips.keepingOrder(listOf("transport", "bills", "food"), spending, selectedId = "shopping")

        assertEquals(listOf("transport", "bills", "shopping"), steady.shown.map { it.id })
    }

    @Test
    fun `with no order yet the chips stay as usage ranks them`() {
        val chips = spending.chips(first = null, usage = usage)

        assertEquals(chips, chips.keepingOrder(null, spending, selectedId = null))
    }

    @Test
    fun `a renamed category stays selected`() {
        val renamed = spending.map { if (it.id == "shopping") it.copy(name = "Shops") else it }

        val chips = renamed.chips(first = spending.byId("shopping"), usage = usage)

        assertEquals("Shops", chips.shown.first().name)
    }

    @Test
    fun `a More sheet pick of the chosen row keeps it`() {
        assertFalse(sheetPickChanges(spending.byId("food"), selectedId = "food"))
        assertTrue(sheetPickChanges(spending.byId("bills"), selectedId = "food"))
    }

    private fun List<Category>.byId(id: String) = first { it.id == id }
}
