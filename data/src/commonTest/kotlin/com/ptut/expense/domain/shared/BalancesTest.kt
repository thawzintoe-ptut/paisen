package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang
import kotlin.test.Test
import kotlin.test.assertEquals

class BalancesTest {

    private val dinner = SharedExpense(
        id = "e1",
        versionId = "v1",
        version = 1,
        groupId = "g1",
        description = "Dinner",
        occurredAtEpochMillis = 0,
        categoryId = null,
        subCategoryId = null,
        note = null,
        currency = "THB",
        amountMinor = 90_000,
        rate = null,
        satang = Satang(90_000),
        payerMemberId = "me",
        splitMode = SplitMode.Equally,
        shares = listOf(
            Share("me", 1, 30_000, Satang(30_000)),
            Share("aye", 1, 30_000, Satang(30_000)),
            Share("bo", 1, 30_000, Satang(30_000)),
        ),
        authorMemberId = "me",
        createdAtEpochMillis = 0,
        removed = false,
    )

    @Test
    fun thePayerLentWhatTheOthersOwe() {
        assertEquals(Satang(60_000), dinner.effectFor("me"))
    }

    @Test
    fun aSharerOwesTheirOwnPart() {
        assertEquals(Satang(-30_000), dinner.effectFor("aye"))
    }

    @Test
    fun someoneOutsideTheExpenseOrNoMemberIsZero() {
        assertEquals(Satang.Zero, dinner.effectFor("dan"))
        assertEquals(Satang.Zero, dinner.effectFor(null))
    }

    @Test
    fun aPayerWithNoPartLentTheWholeAmount() {
        assertEquals(Satang(90_000), dinner.copy(payerMemberId = "dan").effectFor("dan"))
    }
}
