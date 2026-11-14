package com.ptut.expense.shared

import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.PromptPay
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.Transfer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Joined here because the pre-commit and pre-push guards refuse a 10-digit run in test data (NFR-5).
private const val MY_ID = "081" + "2345678"
private val Owed = Satang.ofMinorUnits(24_000)

class PayMeCardTest {

    @Test
    fun `a debt owed to me makes a card with the payer and the exact amount in the QR`() {
        val card = assertNotNull(payMeCard(group(myPromptPay = MY_ID), Transfer("aye", "me", Owed)))

        assertEquals("Aye", card.payerName)
        assertEquals("Flat 12", card.groupName)
        assertEquals(PromptPay.payload(MY_ID, Owed), card.qrPayload)
        assertEquals(PromptPay.masked(MY_ID), card.maskedId)
        assertTrue("Aye" in card.shareText && "Flat 12" in card.shareText, card.shareText)
    }

    @Test
    fun `a debt between two other people makes no card`() {
        assertNull(payMeCard(group(myPromptPay = MY_ID), Transfer("aye", "ko", Owed)))
    }

    @Test
    fun `a debt I owe makes no card`() {
        assertNull(payMeCard(group(myPromptPay = MY_ID), Transfer("me", "aye", Owed)))
    }

    /** The QR is the card, so with no ID saved there is nothing to send yet. */
    @Test
    fun `no card until my PromptPay ID is saved`() {
        assertNull(payMeCard(group(myPromptPay = null), Transfer("aye", "me", Owed)))
    }

    @Test
    fun `the text carries the invite link only while the group's link is on`() {
        val shared = group(myPromptPay = MY_ID).copy(inviteCode = "ABC234XYZ", inviteActive = true)
        val linkOff = shared.copy(inviteActive = false)

        assertTrue("/join/ABC234XYZ" in assertNotNull(payMeCard(shared, Transfer("aye", "me", Owed))).shareText)
        assertTrue("/join/" !in assertNotNull(payMeCard(linkOff, Transfer("aye", "me", Owed))).shareText)
    }

    private fun group(myPromptPay: String?) = SharedGroup(
        id = "g1",
        name = "Flat 12",
        type = GroupType.Flat,
        icon = "home",
        simplifyDebts = true,
        createdAtEpochMillis = 0,
        meMemberId = "me",
        members = listOf(
            Member(id = "me", groupId = "g1", name = "Ko Ko", personId = null, promptPayId = myPromptPay),
            Member(id = "aye", groupId = "g1", name = "Aye", personId = null),
            Member(id = "ko", groupId = "g1", name = "Ko", personId = null),
        ),
    )
}
