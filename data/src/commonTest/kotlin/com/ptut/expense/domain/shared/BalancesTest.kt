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

    @Test
    fun thePayersPartHoldsTheirShareAndWhatTheyLent() {
        assertEquals(MyPart(involved = true, effect = Satang(60_000), share = Satang(30_000)), dinner.partFor("me"))
    }

    @Test
    fun aSharersPartIsTheirShareOwed() {
        assertEquals(MyPart(involved = true, effect = Satang(-30_000), share = Satang(30_000)), dinner.partFor("aye"))
    }

    @Test
    fun aStrangerIsNotInvolved() {
        assertEquals(MyPart(involved = false, effect = Satang.Zero, share = Satang.Zero), dinner.partFor("dan"))
    }

    private val settlement = Settlement(
        transfers = listOf(
            Transfer("aye", "me", Satang(30_000)),
            Transfer("bo", "me", Satang(45_000)),
            Transfer("bo", "aye", Satang(10_000)),
        ),
        pairwiseCount = 3,
        simplifiedCount = 3,
    )

    @Test
    fun owedIsWhatOnePersonPaysTheOtherInThatDirection() {
        assertEquals(Satang(30_000), settlement.owed("aye", "me"))
        assertEquals(Satang.Zero, settlement.owed("me", "aye"))
    }

    @Test
    fun theLargestTransferInvolvingMeIsTheOneToSettle() {
        assertEquals(Transfer("bo", "me", Satang(45_000)), settlement.largestInvolving("me"))
        assertEquals(Transfer("aye", "me", Satang(30_000)), settlement.largestInvolving("aye"))
        assertEquals(null, settlement.largestInvolving("dan"))
    }
}
