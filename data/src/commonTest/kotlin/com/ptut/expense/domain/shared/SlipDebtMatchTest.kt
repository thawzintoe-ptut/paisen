package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val Half = Satang.ofMinorUnits(24_000)
private val Whole = Satang.ofMinorUnits(48_000)

class SlipDebtMatchTest {

    @Test
    fun `a slip for exactly what I owe finds that debt`() {
        val debts = snapshot(flat(aye = "Aye")).debtsPaidBy(Half, payee = "AYE AYE MON")

        assertEquals(listOf(SlipDebt("flat", "Flat 12", "me", "aye", "Aye", Half)), debts)
    }

    @Test
    fun `a slip for another amount finds nothing`() {
        assertTrue(snapshot(flat(aye = "Aye")).debtsPaidBy(Satang.ofMinorUnits(24_001), payee = "AYE AYE MON").isEmpty())
    }

    /** Someone who owes me is not someone I pay: the slip is money going out. */
    @Test
    fun `a debt owed to me is never matched`() {
        val ayePaidMe = flat(aye = "Aye", payer = "me")

        assertTrue(snapshot(ayePaidMe).debtsPaidBy(Half, payee = "AYE AYE MON").isEmpty())
    }

    /** Equal debts in two groups are both offered; the one whose name is on the slip comes first. */
    @Test
    fun `the payee named on the slip comes first when two debts have the amount`() {
        val two = snapshot(flat(aye = "Aye"), flat(aye = "Ko Min", id = "trip", name = "Trip"))

        val debts = two.debtsPaidBy(Half, payee = "MR. KO MIN")

        assertEquals(listOf("Ko Min", "Aye"), debts.map { it.toName })
    }

    private fun snapshot(vararg groups: Pair<SharedGroup, SharedExpense>) = SharedSnapshot(
        groups = groups.map { it.first },
        people = emptyList(),
        expenseVersions = groups.map { it.second },
        payments = emptyList(),
        links = emptyMap(),
    )

    /** Two people split a ฿480 dinner equally, so whoever did not pay owes ฿240. */
    private fun flat(aye: String, payer: String = "aye", id: String = "flat", name: String = "Flat 12"): Pair<SharedGroup, SharedExpense> {
        val group = SharedGroup(
            id = id,
            name = name,
            type = GroupType.Flat,
            icon = "home",
            simplifyDebts = true,
            createdAtEpochMillis = 0,
            meMemberId = "me",
            members = listOf(
                Member(id = "me", groupId = id, name = "Ko Ko", personId = null),
                Member(id = "aye", groupId = id, name = aye, personId = null),
            ),
        )
        val dinner = SharedExpense(
            id = "$id-dinner",
            versionId = "$id-v1",
            version = 1,
            groupId = id,
            description = "Dinner",
            occurredAtEpochMillis = 0,
            categoryId = null,
            subCategoryId = null,
            note = null,
            currency = Currencies.Thb.code,
            amountMinor = Whole.toMinorUnits(),
            rate = null,
            satang = Whole,
            payerMemberId = payer,
            splitMode = SplitMode.Equally,
            shares = listOf(Share("me", 1, Half.toMinorUnits(), Half), Share("aye", 1, Half.toMinorUnits(), Half)),
            authorMemberId = payer,
            createdAtEpochMillis = 0,
            removed = false,
        )
        return group to dinner
    }
}
