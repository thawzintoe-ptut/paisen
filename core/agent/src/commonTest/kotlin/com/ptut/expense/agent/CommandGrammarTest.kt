package com.ptut.expense.agent

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.SubCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

class CommandGrammarTest {

    private val grammar = CommandGrammar()
    private val now = Instant.parse("2026-10-15T05:00:00Z")
    private val treats = Category("treats", "Treats", "cake")
    private val coffee = SubCategory(id = "coffee", categoryId = "food", name = "Coffee")
    private val thaiCoffee = SubCategory(id = "thai-coffee", categoryId = "food", name = "กาแฟ")
    private val burmeseCoffee = SubCategory(id = "burmese-coffee", categoryId = "food", name = "ကော်ဖီ")

    private fun added(text: String, subCategories: List<SubCategory>, categories: List<Category> = Category.seed()) =
        assertIs<GrammarReading.Full>(grammar.read(text, categories, emptyList(), now, subCategories)).call as ProposedAction.AddTransaction

    @Test
    fun aSubCategoryNamedInEnglishIsPickedUnderItsCategory() {
        val call = added("coffee 60", listOf(coffee))

        assertEquals("food", call.categoryId)
        assertEquals("coffee", call.subCategoryId)
    }

    @Test
    fun aSubCategoryNamedInThaiIsPicked() {
        assertEquals("thai-coffee", added("กาแฟ 60", listOf(thaiCoffee)).subCategoryId)
    }

    @Test
    fun aSubCategoryNamedInBurmeseIsPicked() {
        assertEquals("burmese-coffee", added("ကော်ဖီ 60", listOf(burmeseCoffee)).subCategoryId)
    }

    @Test
    fun theSubCategorysOwnParentWinsOverTheSeedWords() {
        val mine = SubCategory(id = "my-coffee", categoryId = "treats", name = "Coffee")

        val call = added("coffee 60", listOf(mine), categories = Category.seed() + treats)

        assertEquals("treats", call.categoryId)
        assertEquals("my-coffee", call.subCategoryId)
    }

    @Test
    fun aSubCategoryUnderAnotherNamedCategoryIsLeftUnpicked() {
        val call = added("transport coffee 60", listOf(coffee))

        assertEquals("transport", call.categoryId)
        assertNull(call.subCategoryId)
    }

    @Test
    fun aNoteThatOnlyRepeatsTheSubCategoryIsNoNote() {
        assertNull(added("coffee 60", listOf(coffee)).memo)
    }
}
