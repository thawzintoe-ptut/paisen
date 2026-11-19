package com.ptut.expense.shared

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SplitMode
import com.ptut.expense.domain.shared.membersToSplit
import com.ptut.expense.domain.shared.money
import com.ptut.expense.locale.AppText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AddExpenseUiMapperTest {

    private val categories = Category.seed()
    private val mapper = AddExpenseUiMapper(categories, isEdit = false)

    @Test
    fun `exact shares that miss the total say so`() {
        val split = ready(form(amount = "100", mode = SplitMode.Exact, weights = mapOf("me" to "30", "aye" to "30"))).split

        assertNotNull(split.leftTitle)
        assertEquals(false, split.complete)
    }

    @Test
    fun `a percent split short of a hundred says what is left and saves nothing`() {
        val ready = ready(form(amount = "100", mode = SplitMode.Percent, weights = mapOf("me" to "60")))

        assertEquals(false, ready.split.complete)
        assertEquals(false, ready.canSave)
        assertEquals(AppText.copy.shared.expense.leftToAssign("40%"), ready.split.leftTitle)
    }

    @Test
    fun `a share with a decimal says 1 point 5 counts as 1`() {
        val ready = ready(form(amount = "90", mode = SplitMode.Shares, weights = mapOf("me" to "1.5", "aye" to "1")))

        val mine = ready.split.rows.single { it.memberId == "me" }
        assertEquals("1.5 counts as 1 · ฿45.00", mine.detail)
    }

    @Test
    fun `an equal split that does not divide hands the odd satang to one person`() {
        val bo = Member(id = "bo", groupId = "g1", name = "Bo", personId = null)
        val ready = ready(form(amount = "100"), group = flat12.copy(members = flat12.members + bo))

        assertEquals(listOf("฿33.33", "฿33.33", "฿33.34"), ready.split.rows.map { it.part }.sorted())
        assertEquals("you are owed ฿66.66", ready.resultLine)
    }

    private fun form(amount: String, mode: SplitMode = SplitMode.Equally, weights: Map<String, String> = emptyMap()) = ExpenseForm(
        currency = flat12.money,
        amountText = amount,
        categoryId = categories.first().id,
        payerMemberId = "me",
        splitMode = mode,
        weightTexts = weights,
        loaded = true,
    )

    private fun ready(form: ExpenseForm, group: SharedGroup = flat12) =
        mapper.ready(group, form, group.membersToSplit(form.splitMode, sharers = emptySet()))
}
