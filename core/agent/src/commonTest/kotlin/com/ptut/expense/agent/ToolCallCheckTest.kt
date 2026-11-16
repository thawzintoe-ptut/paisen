package com.ptut.expense.agent

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.SharedGroup
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class ToolCallCheckTest {

    private val check = ToolCallCheck()

    @AfterTest
    fun backToBaht() = Home.use(HomeChoice.Default)

    // #48

    @Test
    fun aGroupAmountIsReadInTheGroupsMoneyNotTheLedgers() {
        Home.use(HomeChoice(HomeMoney("MMK", "K", 0), "Asia/Yangon"))

        val call = valid(groupExpense("5000", group("Trip", "MMK")))

        assertEquals(Satang.ofMinorUnits(5_000), call.amount)
        assertEquals(0, call.amountDigits)
    }

    @Test
    fun aBahtGroupInAKyatLedgerKeepsItsTwoDecimals() {
        Home.use(HomeChoice(HomeMoney("MMK", "K", 0), "Asia/Yangon"))

        val call = valid(groupExpense("60.50", group("Trip", "THB")))

        assertEquals(Satang.ofMinorUnits(6_050), call.amount)
        assertEquals(2, call.amountDigits)
    }

    @Test
    fun aFractionTheGroupsMoneyDoesNotHaveIsRefusedByName() {
        val refused = assertIs<CheckedCall.Invalid>(groupExpense("60.5", group("Trip", "MMK")))

        assertEquals("amount 60.5 is not money with at most 0 decimals.", refused.error)
    }

    private fun valid(checked: CheckedCall) = assertIs<CheckedCall.Valid>(checked).call as ProposedAction.AddGroupExpense

    private fun groupExpense(amount: String, group: SharedGroup) = check.check(
        ModelReply("add_group_expense", mapOf("group" to group.name, "amount" to amount)),
        AgentContext(categories = Category.seed(), subCategories = emptyList(), groups = listOf(group), rowIds = emptySet(), now = Instant.parse("2026-10-15T05:00:00Z")),
    )

    private fun group(name: String, currency: String) = SharedGroup(
        id = name.lowercase(),
        name = name,
        type = GroupType.Trip,
        icon = "trip",
        simplifyDebts = true,
        createdAtEpochMillis = 0,
        meMemberId = null,
        members = emptyList(),
        currency = currency,
    )
}
