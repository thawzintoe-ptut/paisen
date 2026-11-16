package com.ptut.expense.domain.slip

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Satang
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The 2C2P WAVE e-receipt (#39), laid out as in Wave Money's own picture of it. No real receipt has
 * been read by ML Kit yet, so both line orders a two-column row can come back in are covered.
 */
class WaveSlipTest {

    private companion object {
        const val SENDER_WALLET = "xxxxxxxxxxx" + "1234"
        const val RECEIVER_WALLET = "xxxxxxxxxxx" + "5678"
    }

    private fun receipt(
        date: String = "16/05/2025, 08:31 AM",
        values: List<String> = listOf("Amount", "1,000.00 THB", "Fee", "0.00 THB", "Total", "1,000.00 THB"),
        extra: List<String> = emptyList(),
    ) = listOf(
        "E-Receipt",
        "Success",
        date,
        "2C2P wave",
        "Transfer",
        "MR. AUNG KO",
        SENDER_WALLET,
        "MS. THIDA WIN",
        RECEIVER_WALLET,
    ) + values + listOf("Note", "Transfer") + extra + listOf("Share", "Done")

    private fun bangkok(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime(year, month, day, hour, minute).toInstant(BangkokTime).toEpochMilliseconds()

    @Test
    fun `a transfer reads the receiver and the amount and the Bangkok time`() {
        val read = assertIs<SlipRead.Partial>(WaveSlip.parse(receipt()))

        assertEquals("MS. THIDA WIN", read.counterparty)
        assertEquals(Satang.ofMinorUnits(100_000), read.amount)
        assertEquals(bangkok(2025, 5, 16, 8, 31), read.occurredAtEpochMillis)
        assertEquals(null, read.reference)
    }

    @Test
    fun `a receipt with a transaction number is complete`() {
        val read = assertIs<SlipRead.Complete>(
            WaveSlip.parse(receipt(extra = listOf("Transaction ID", "TX2025AB0099"))),
        )

        assertEquals("TX2025AB0099", read.reference)
    }

    @Test
    fun `labels read before their values still give the amount rather than the fee`() {
        val columns = listOf("Amount", "Fee", "Total", "250.50 THB", "0.00 THB", "250.50 THB")

        val read = assertIs<SlipRead.Partial>(WaveSlip.parse(receipt(values = columns)))

        assertEquals(Satang.ofMinorUnits(25_050), read.amount)
        assertEquals(setOf(SlipField.Amount), read.lowConfidence)
    }

    @Test
    fun `an evening time is read as PM`() {
        val read = assertIs<SlipRead.Partial>(WaveSlip.parse(receipt(date = "3/1/2026, 07:05 PM")))

        assertEquals(bangkok(2026, 1, 3, 19, 5), read.occurredAtEpochMillis)
    }

    @Test
    fun `a receipt with no readable name is still a slip`() {
        val lines = receipt().filterNot { it.startsWith("MR.") || it.startsWith("MS.") }

        assertIs<SlipRead.Unreadable>(WaveSlip.parse(lines))
        assertTrue(SlipParser.hasSlipLabels(lines))
    }

    @Test
    fun `the parser hands a WAVE receipt to the WAVE reader`() {
        val read = assertIs<SlipRead.Partial>(SlipParser.parse(receipt()))

        assertEquals("MS. THIDA WIN", read.counterparty)
    }

    @Test
    fun `a K PLUS slip is not taken for a WAVE receipt`() {
        val kplus = listOf(
            "Payment Completed",
            "14 Aug 26  7:58 PM",
            "MR. SOMCHAI J",
            "KBank",
            "xxx-x-xxxxx-x",
            "LAWSON",
            "Amount:",
            "50.00 Baht",
        )

        assertFalse(WaveSlip.recognises(kplus))
        assertEquals("LAWSON", assertIs<SlipRead.Partial>(SlipParser.parse(kplus)).counterparty)
    }
}
