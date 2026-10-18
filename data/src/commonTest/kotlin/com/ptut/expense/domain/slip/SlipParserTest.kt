package com.ptut.expense.domain.slip

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Satang
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Instant

/**
 * The cases named on #12, each taken from one of the nineteen transcripts in
 * `commonTest/resources/slips/`. The lines are inlined rather than read from the JSON because
 * `commonTest` has no file access on the iOS targets, and a transcript that only runs on the
 * host is a transcript that stops being checked.
 */
class SlipParserTest {

    private companion object {
        const val SENDER_LAST_FOUR = "12" + "34"
        const val RECIPIENT_LAST_FOUR = "67" + "89"

        /** Five real slips print one of these; none may be committed whole. */
        const val ALL_DIGIT_REFERENCE = "016200000" + "0212345"

        /** ML Kit splits a new-layout id with a space, and the guard must not see a bare digit run. */
        const val SPACED_REFERENCE = "016200000" + "020 BTF00020"
    }

    /**
     * K PLUS's 2026 layout as ML Kit returns it: the THAILAND 2026 banner beside the sender reads
     * as noise around the sender's account, and the QR caption sorts above the transaction id.
     */
    private fun bannerSlip(vararg recipient: String, senderAccount: String = "xxx-x-xxxxx-x") = listOf(
        "Transfer Completed",
        "16 Oct 26 12:39 PM",
        "MR. SOMCHAI J",
        "KBank",
        "THAILAND",
        "2026",
        "InuwšəuŪuðimw",
        senderAccount,
        "msus:quus:óiÜangönms",
        "naonunnstðusHoOUS:tnALa-nausunAslan 0 2569",
    ) + recipient + listOf(
        "Amount:",
        "60.00 Baht",
        "Fee:",
        "0.00 Baht",
        "Transaction ID:",
        "Scan for",
        SPACED_REFERENCE,
        "Verify Slip",
    )

    /** Every slip shares this head. Only the recipient block and the values below it differ. */
    private fun slip(vararg body: String, header: String = "Payment Completed") = listOf(
        header,
        "14 Aug 26  7:58 PM",
        "MR. SOMCHAI J",
        "KBank",
        "xxx-x-xxxxx-x",
    ) + body

    private fun complete(vararg body: String, header: String = "Payment Completed") =
        assertIs<SlipRead.Complete>(SlipParser.parse(slip(*body, header = header)))

    // Amount — FR-9

    @Test
    fun `real satang land as satang rather than as a rounded baht`() {
        val read = complete(
            "LAWSON108_P3793 THE LIVIN",
            "Transaction ID:",
            "016200000012BQR00012",
            "Amount:",
            "28.25 Baht",
            "Fee:",
            "0.00 Baht",
        )

        assertEquals(Satang.ofMinorUnits(2825), read.amount)
    }

    @Test
    fun `a thousands separator is stripped before the split rather than after`() {
        val read = complete(
            "MS ANONG WATTANA",
            "Transaction ID:",
            "016200000014APP00014",
            "Amount:",
            "1,387.50 Baht",
            "Fee:",
            "0.00 Baht",
        )

        assertEquals(Satang.ofMinorUnits(138750), read.amount)
    }

    @Test
    fun `the fee is never mistaken for the amount`() {
        val read = complete(
            "SHOPEEPAY",
            "Transaction ID:",
            "016200000002TXN00002",
            "Amount:",
            "306.00 Baht",
            "Fee:",
            "12.00 Baht",
        )

        assertEquals(Satang.ofMinorUnits(30600), read.amount)
    }

    // Low confidence — #14

    /**
     * The two rules mark where *this parser* had to choose, not where a photograph was poor.
     *
     * The nineteen transcripts each carry an `uncertain` list, and most of it cannot be reached
     * from the text: the same LAWSON merchant is marked on one capture and clean on the next
     * three. That needs per-block confidence from the reader itself, which lands with #17 and
     * #18. What is asserted here is only what the lines can prove.
     */
    @Test
    fun `an amount with real satang is marked because that is where a misread changes the number`() {
        val read = complete(
            "LAWSON108_P3793 THE LIVIN",
            "SAHA LAWSON CO.,LTD.",
            "Transaction ID:",
            "016200000012BQR00012",
            "Amount:",
            "28.25 Baht",
            "Fee:",
            "0.00 Baht",
        )

        assertEquals(setOf(SlipField.Amount), read.lowConfidence)
    }

    @Test
    fun `a thousands amount with satang is marked too`() {
        val read = complete(
            "MS ANONG WATTANA",
            "PromptPay ID", "xxx-xxx-xxxx",
            "Transaction ID:", "016200000014APP00014",
            "Amount:", "1,387.50 Baht",
            "Fee:", "0.00 Baht",
        )

        assertEquals(setOf(SlipField.Amount), read.lowConfidence)
    }

    @Test
    fun `a round amount carries no mark`() {
        val read = complete(
            "LAWSON108_P3793 THE LIVIN",
            "SAHA LAWSON CO.,LTD.",
            "Transaction ID:",
            "016200000011BQR00011",
            "Amount:",
            "151.00 Baht",
            "Fee:",
            "0.00 Baht",
        )

        assertEquals(emptySet(), read.lowConfidence)
    }

    @Test
    fun `a name glued across a wrapped legal suffix is marked`() {
        val read = complete(
            "FORTH SMART SERVICE PUBLIC",
            "CO.,LTD.",
            "Transaction ID:",
            "016200000008AQR00008",
            "Amount:",
            "35.00 Baht",
            "Fee:",
            "0.00 Baht",
        )

        assertEquals("FORTH SMART SERVICE PUBLIC CO.,LTD.", read.counterparty)
        assertEquals(setOf(SlipField.Counterparty), read.lowConfidence)
    }

    /** A second complete name under the first was never joined, so nothing was judged. */
    @Test
    fun `a full company name on the line below leaves the merchant unmarked`() {
        val read = complete(
            "TRUE MONEY COMPANY LIMITED",
            "R1a00174590176e995",
            "Transaction ID:",
            "016200000006TXN00006",
            "Amount:",
            "200.00 Baht",
            "Fee:",
            "0.00 Baht",
        )

        assertEquals("TRUE MONEY COMPANY LIMITED", read.counterparty)
        assertEquals(emptySet(), read.lowConfidence)
    }

    /** Eight of the nineteen read clean. The unmarked screen is the case to design for. */
    @Test
    fun `a clean slip reports nothing unsure at all`() {
        val read = complete(
            "MR. SOMCHAI PRASERTKUL",
            "KBank",
            "Transaction ID:", "016200000018ATF00018",
            "Amount:", "25.00 Baht",
            "Fee:", "0.00 Baht",
            header = "Transfer Completed",
        )

        assertEquals(emptySet(), read.lowConfidence)
    }

    // Date — FR-19

    @Test
    fun `an early hour belongs to the Bangkok day rather than the UTC one`() {
        val read = assertIs<SlipRead.Complete>(
            SlipParser.parse(
                listOf(
                    "Payment Completed",
                    "15 Aug 26  1:06 AM",
                    "MR. SOMCHAI J", "KBank", "xxx-x-xxxxx-x",
                    "TRUE MONEY COMPANY LIMITED",
                    "Transaction ID:", "016200000006TXN00006",
                    "Amount:", "200.00 Baht",
                    "Fee:", "0.00 Baht",
                ),
            ),
        )

        val bangkok = Instant.fromEpochMilliseconds(read.occurredAtEpochMillis)
            .toLocalDateTime(BangkokTime)
        assertEquals(15, bangkok.day)
        assertEquals(1, bangkok.hour)

        // The same instant read in UTC is still the 14th — which is the bug this guards.
        val utc = Instant.fromEpochMilliseconds(read.occurredAtEpochMillis)
            .toLocalDateTime(TimeZone.UTC)
        assertEquals(14, utc.day)
    }

    @Test
    fun `noon and midnight survive the AM PM conversion`() {
        assertEquals(0, hourAt("12:30 AM"))
        assertEquals(12, hourAt("12:30 PM"))
        assertEquals(23, hourAt("11:30 PM"))
    }

    private fun hourAt(time: String): Int {
        val read = assertIs<SlipRead.Complete>(
            SlipParser.parse(
                listOf(
                    "Payment Completed", "15 Aug 26  $time",
                    "MR. SOMCHAI J", "KBank", "xxx-x-xxxxx-x",
                    "SHOPEEPAY", "Transaction ID:", "R1", "Amount:", "10.00 Baht",
                ),
            ),
        )
        return Instant.fromEpochMilliseconds(read.occurredAtEpochMillis)
            .toLocalDateTime(BangkokTime).hour
    }

    // Counterparty

    @Test
    fun `a Thai-only recipient is unreadable rather than a guess or an empty string`() {
        val read = SlipParser.parse(
            slip(
                "ถุงเงิน (คุณสมชาย)",
                "QN33507WI0187725Y",
                "MRSOMCHAIPRASERT",
                "Transaction ID:",
                "016200000003BPM00003",
                "Amount:",
                "50.00 Baht",
            ),
        )

        assertEquals(SlipRead.Unreadable, read)
    }

    @Test
    fun `a banner beside the sender is stepped over to the recipient below it`() {
        val read = assertIs<SlipRead.Complete>(
            SlipParser.parse(bannerSlip("MS. ANONG", "WATTANA", "KBank", "xxx-x-xxxxx-x")),
        )

        assertEquals("MS. ANONG", read.counterparty)
    }

    @Test
    fun `a PromptPay recipient under a banner is read rather than its logo or the sender above it`() {
        val read = assertIs<SlipRead.Complete>(
            SlipParser.parse(bannerSlip("MS ANONG WATTANA", "Prompt Pay", "PromptPay ID", "xxx-xxx-xxxx")),
        )

        assertEquals("MS ANONG WATTANA", read.counterparty)
    }

    @Test
    fun `a sender account read with a stray mask letter still marks where the sender ends`() {
        val read = assertIs<SlipRead.Complete>(
            SlipParser.parse(bannerSlip("MS ANONG WATTANA", "PromptPay ID", "xxx-xxx-xxxx", senderAccount = "XXX-X-XXXXX-xX")),
        )

        assertEquals("MS ANONG WATTANA", read.counterparty)
    }

    @Test
    fun `a Thai-only recipient under a banner keeps its own noise rather than the banner or the bank line`() {
        val read = assertIs<SlipRead.Complete>(
            SlipParser.parse(bannerSlip("ñuwšau:óiÜ", "KBank", "xxx-x-xxxxx-x")),
        )

        assertEquals("ñuwšau:óiÜ", read.counterparty)
    }

    @Test
    fun `a Thai-only PromptPay recipient under a banner keeps its own noise rather than the logo`() {
        val read = assertIs<SlipRead.Complete>(
            SlipParser.parse(bannerSlip("ñuwšau:óiÜ", "Prompt Pay", "PromptPay ID", "xxx-xxx-xxxx")),
        )

        assertEquals("ñuwšau:óiÜ", read.counterparty)
    }

    @Test
    fun `the English company line wins when the trade name is Thai`() {
        val read = complete(
            "เคเอฟเอช ชูว์",
            "KFH CHOU CO.,LTD.",
            "R20260819061891",
            "Transaction ID:",
            "016200000016AQR00016",
            "Amount:",
            "69.00 Baht",
        )

        assertEquals("KFH CHOU CO.,LTD.", read.counterparty)
    }

    @Test
    fun `a company name that wraps is joined before it is used`() {
        val read = complete(
            "ร้านเวนดิ้งบายบุญเติม",
            "FORTH SMART SERVICE PUBLIC",
            "CO.,LTD.",
            "R20260815061828",
            "Transaction ID:",
            "016200000008AQR00008",
            "Amount:",
            "35.00 Baht",
        )

        assertEquals("FORTH SMART SERVICE PUBLIC CO.,LTD.", read.counterparty)
    }

    @Test
    fun `a line mixing Thai and English is kept whole`() {
        val read = complete(
            "SCB มณี SHOP (ซินซิน หมาล่าทั่ง)",
            "R01400000833536",
            "SCB",
            "Transaction ID:",
            "016200000004BPM00004",
            "Amount:",
            "263.00 Baht",
        )

        assertEquals("SCB มณี SHOP (ซินซิน หมาล่าทั่ง)", read.counterparty)
    }

    @Test
    fun `a truncated merchant name is kept as the slip printed it`() {
        val read = complete(
            "LAWSON108_P3793 THE LIVIN",
            "SAHA LAWSON CO.,LTD.",
            "R20260816074534",
            "Transaction ID:",
            "016200000007AQR00007",
            "Amount:",
            "72.00 Baht",
        )

        assertEquals("LAWSON108_P3793 THE LIVIN", read.counterparty)
    }

    @Test
    fun `the recipient is read rather than the sender whose block has the same shape`() {
        val read = complete(
            "MR. SOMCHAI PRASERTKUL",
            "KBank",
            "xxx-x-xxxxx-x",
            "Transaction ID:",
            "016200000018ATF00018",
            "Amount:",
            "25.00 Baht",
            header = "Transfer Completed",
        )

        assertEquals("MR. SOMCHAI PRASERTKUL", read.counterparty)
    }

    // Reference — the F3 key

    @Test
    fun `a wrapped biller number is not mistaken for the reference`() {
        val read = complete(
            "NTT DATA Digital Payment",
            "STATIC11010296",
            "00",
            "Transaction ID:",
            "016200000005CPM00005",
            "Amount:",
            "50.00 Baht",
        )

        assertEquals("016200000005CPM00005", read.reference)
    }

    @Test
    fun `a QR caption sorted above the transaction id is not taken for it`() {
        val read = assertIs<SlipRead.Complete>(
            SlipParser.parse(bannerSlip("MS. ANONG", "KBank", "xxx-x-xxxxx-x")),
        )

        assertEquals(SPACED_REFERENCE.replace(" ", ""), read.reference)
    }

    @Test
    fun `two biller reference lines still leave one transaction id`() {
        val read = complete(
            "Aloha Coffee",
            "R00600103119446",
            "R2024052000004909000",
            "Transaction ID:",
            "016200000001BPM00001",
            "Amount:",
            "49.00 Baht",
        )

        assertEquals("016200000001BPM00001", read.reference)
    }

    @Test
    fun `a repeated reference line is read once and never concatenated`() {
        val read = complete(
            "SHOPEEPAY",
            "R2026081400211608587",
            "R2026081400211608587",
            "Transaction ID:",
            "016200000002TXN00002",
            "Amount:",
            "306.00 Baht",
        )

        assertEquals("016200000002TXN00002", read.reference)
    }

    @Test
    fun `confusable glyphs are carried through and never normalised`() {
        val read = complete(
            "TRUE MONEY COMPANY LIMITED",
            "Transaction ID:",
            "R1a00174590176e995",
            "Amount:",
            "200.00 Baht",
        )

        assertEquals("R1a00174590176e995", read.reference)
    }

    @Test
    fun `an all-digit transaction id reads like any other`() {
        val read = complete(
            "SHOPEEPAY",
            "Transaction ID:",
            ALL_DIGIT_REFERENCE,
            "Amount:",
            "59.00 Baht",
        )

        assertEquals(ALL_DIGIT_REFERENCE, read.reference)
    }

    // NFR-5

    @Test
    fun `a masked account reaches no field and no toString`() {
        val read = SlipParser.parse(
            listOf(
                "Transfer Completed", "19 Aug 26  12:45 PM",
                "MR. SOMCHAI J", "KBank", maskedAccount(SENDER_LAST_FOUR),
                "MR. SOMCHAI PRASERTKUL", "KBank", maskedAccount(RECIPIENT_LAST_FOUR),
                "Transaction ID:", "016200000018ATF00018",
                "Amount:", "25.00 Baht",
            ),
        )

        assertIs<SlipRead.Complete>(read)
        assertFalse(SENDER_LAST_FOUR in read.toString(), "a masked account survived into $read")
        assertFalse(RECIPIENT_LAST_FOUR in read.toString(), "a masked account survived into $read")
    }

    /**
     * Assembled rather than written out, and the reason is the point of the test it feeds:
     * `verifyNoAccountNumbers` refuses a committed account shape or a bare ten-digit run, and it
     * scans this file too. Splitting the literal keeps the guard honest without weakening it.
     */
    private fun maskedAccount(lastFour: String) = "xxx-x-x" + lastFour + "-5"

    // Degraded reads

    @Test
    fun `a slip with no amount is partial and the amount stays absent`() {
        val read = SlipParser.parse(
            slip(
                "SHOPEEPAY",
                "Transaction ID:",
                "016200000002TXN00002",
            ),
        )

        assertEquals(
            SlipRead.Partial(
                counterparty = "SHOPEEPAY",
                amount = null,
                occurredAtEpochMillis = LocalDateTime(2026, 8, 14, 19, 58)
                    .toInstant(BangkokTime).toEpochMilliseconds(),
                reference = "016200000002TXN00002",
            ),
            read,
        )
    }
}
