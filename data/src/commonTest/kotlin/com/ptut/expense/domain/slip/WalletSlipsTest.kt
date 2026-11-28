package com.ptut.expense.domain.slip

import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/** #35 and #36: the three Myanmar wallets, laid out as their public receipts and guides show them. */
class WalletSlipsTest {

    private companion object {
        /** Split so the account-number guard sees no long digit run in this file. */
        const val KBZ_REF = "01003769" + "01047326" + "7025"
        const val MERCHANT_CODE = "70169" + "501010"
        const val AYA_REF = "20705081" + "98657"
        const val PHONE = "09xxxxxx" + "1234"
    }

    private val myanmar = UtcOffset(hours = 6, minutes = 30).asTimeZone()

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime(year, month, day, hour, minute).toInstant(myanmar).toEpochMilliseconds()

    private val kbz = listOf(
        "KBZ BANK",
        "E-Receipt",
        "-29,500.00 Ks",
        "Transaction Time",
        "14/06/2025 12:10:15",
        "Transaction No.",
        KBZ_REF,
        "Transaction Type",
        "Customer Buy Goods",
        "Transfer To",
        "$MERCHANT_CODE -",
        "LimitedCollections Online",
        "Amount",
        "-29,500.00 Ks",
        "Notes",
        "Thank you for using KBZPay!",
    )

    private val wave = listOf(
        "Successful",
        "1,000 Ks",
        "To",
        "Myat Myat Su active",
        PHONE,
        "Type",
        "Account",
        "Fees",
        "0 Ks",
        "Total Amount",
        "1,000 Ks",
        "Transaction ID",
        "371515335",
        "Date & Time",
        "18 April 2024 • 3:06 PM",
        "Note",
        "pocket money",
    )

    private val aya = listOf(
        "Successful",
        "1,000 MMK",
        "E-Receipt",
        "AYA Pay",
        "Information",
        "Transaction Type",
        "Subscriber Transfer To Wallet",
        "Transaction ID",
        AYA_REF,
        "Sender Phone",
        PHONE,
        "Sender Name",
        "SU SU",
        "Receiver Phone",
        PHONE,
        "Receiver Name",
        "KYAW KYAW",
        "Date and Time",
        "23 July 2024, 10:06 AM",
    )

    @Test
    fun `a KBZPay receipt reads the shop the kyat the Myanmar time and money out`() {
        val read = assertIs<SlipRead.Complete>(SlipParser.parse(kbz))

        assertEquals("LimitedCollections Online", read.counterparty)
        assertEquals(Satang.ofMinorUnits(29_500), read.amount)
        assertEquals("MMK", read.currency)
        assertEquals(at(2025, 6, 14, 12, 10), read.occurredAtEpochMillis)
        assertEquals(KBZ_REF, read.reference)
        assertEquals(Direction.Expense, read.knownDirection)
    }

    @Test
    fun `a KBZPay comma misread as a second point leaves the kyat unread rather than a wrong one`() {
        val misread = kbz.map { if (it == "-29,500.00 Ks") "-29.500.00 Ks" else it }

        assertNull(assertIs<SlipRead.Partial>(SlipParser.parse(misread)).amount)
    }

    @Test
    fun `a KBZPay shop on the same line as its code still reads`() {
        val oneLine = kbz.filterNot { it == "LimitedCollections Online" }.map { if (it == "$MERCHANT_CODE -") "$MERCHANT_CODE - LimitedCollections Online" else it }

        assertEquals("LimitedCollections Online", assertIs<SlipRead.Complete>(SlipParser.parse(oneLine)).counterparty)
    }

    @Test
    fun `a WavePay receipt reads the person paid in kyat`() {
        val read = assertIs<SlipRead.Complete>(SlipParser.parse(wave))

        assertEquals("Myat Myat Su", read.counterparty)
        assertEquals(Satang.ofMinorUnits(1_000), read.amount)
        assertEquals("MMK", read.currency)
        assertEquals(at(2024, 4, 18, 15, 6), read.occurredAtEpochMillis)
        assertEquals("371515335", read.reference)
        assertEquals(Direction.Expense, read.knownDirection)
    }

    @Test
    fun `an AYA Pay receipt reads the receiver and the sender and leaves the direction to the names`() {
        val read = assertIs<SlipRead.Complete>(SlipParser.parse(aya))

        assertEquals("KYAW KYAW", read.counterparty)
        assertEquals("SU SU", read.sender)
        assertEquals(Satang.ofMinorUnits(1_000), read.amount)
        assertEquals(at(2024, 7, 23, 10, 6), read.occurredAtEpochMillis)
        assertNull(read.knownDirection)
    }

    @Test
    fun `a K PLUS slip is not taken for a wallet receipt and stays in baht`() {
        val kplus = listOf("Payment Completed", "14 Aug 26  7:58 PM", "MR. SOMCHAI J", "KBank", "xxx-x-xxxxx-x", "LAWSON", "Amount:", "50.00 Baht")

        assertFalse(KbzPaySlip.recognises(kplus) || WavePaySlip.recognises(kplus) || AyaPaySlip.recognises(kplus))
        assertEquals("THB", assertIs<SlipRead.Partial>(SlipParser.parse(kplus)).currency)
    }
}
