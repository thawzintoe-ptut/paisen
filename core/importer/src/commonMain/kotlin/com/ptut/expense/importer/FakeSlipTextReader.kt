package com.ptut.expense.importer

import com.ptut.expense.platform.ocr.SlipTextReader

/**
 * A reader whose "image" is the transcript itself, decoded as UTF-8.
 *
 * It ships in `commonMain` rather than in the test source set on purpose. It is a permanent test
 * double, not scaffolding: it backs the debug menu's inject-a-fixture-slip action, so the whole
 * pipeline can be exercised on a device with no photo library and no OCR model. That also makes it
 * the reader the host suite uses, which is what keeps every rule in [SlipImporter] testable.
 */
class FakeSlipTextReader : SlipTextReader {
    override suspend fun read(bytes: ByteArray): List<String> =
        bytes.decodeToString().lines().filter { it.isNotBlank() }
}

/**
 * Transcripts for the debug menu. Three, deliberately: the full set lives in
 * `commonTest/resources/slips/` where it belongs, and a shipped app does not need nineteen.
 *
 * [readable] and [readableSameMerchant] are two different payments to the same shop, which is
 * what FR-11 needs to be seen: confirm the first and the second files itself. The same transcript
 * twice would prove something else — that F3 refuses a payment it has already recorded.
 */
object SlipTranscripts {

    /** Reads in full, and its merchant repeats across four real slips. */
    val readable: String = """
        Payment Completed
        15 Aug 26  9:25 AM
        MR. SOMCHAI J
        KBank
        xxx-x-xxxxx-x
        LAWSON108_P3793 THE LIVIN
        SAHA LAWSON CO.,LTD.
        Transaction ID:
        016200000007AQR00007
        Amount:
        72.00 Baht
        Fee:
        0.00 Baht
    """.trimIndent()

    /** The same shop, a different day and a different amount. Files itself once the rule exists. */
    val readableSameMerchant: String = """
        Payment Completed
        16 Aug 26  10:44 AM
        MR. SOMCHAI J
        KBank
        xxx-x-xxxxx-x
        LAWSON108_P3793 THE LIVIN
        SAHA LAWSON CO.,LTD.
        Transaction ID:
        016200000011BQR00011
        Amount:
        151.00 Baht
        Fee:
        0.00 Baht
    """.trimIndent()

    /** C-3: a Thai-only merchant. The reader ships no Thai model, so this one cannot be filed. */
    val unreadable: String = """
        Payment Completed
        14 Aug 26  12:41 PM
        MR. SOMCHAI J
        KBank
        xxx-x-xxxxx-x
        ถุงเงิน (คุณสมชาย)
        Transaction ID:
        016200000003BPM00003
        Amount:
        50.00 Baht
    """.trimIndent()
}
