package com.ptut.expense.domain.slip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderNoiseTest {

    @Test
    fun `a Thai biller name read by the Latin model is noise`() {
        assertTrue("msus:quus:óiÜangönms".isReaderNoise())
    }

    @Test
    fun `a colon inside a word is enough on its own`() {
        assertTrue("nsuu:wws".isReaderNoise())
    }

    @Test
    fun `two accented letters are enough on their own`() {
        assertTrue("óiÜangonms".isReaderNoise())
    }

    @Test
    fun `every name the recorded slips print reads as a name`() {
        val recorded = listOf(
            "Aloha Coffee",
            "NTT DATA Digital Payment",
            "FORTH SMART SERVICE PUBLIC CO.,LTD.",
            "LAWSON108_P3793 THE LIVIN",
            "SAHA LAWSON CO.,LTD.",
            "MR. SOMCHAI J",
            "ShopeePay",
            "TrueMoney Wallet",
        )
        recorded.forEach { assertFalse(it.isReaderNoise(), it) }
    }

    @Test
    fun `one accent is a real name such as Cafe Amazon`() {
        assertFalse("Café Amazon".isReaderNoise())
    }

    @Test
    fun `a time or a label colon is not noise`() {
        assertFalse("14 Aug 26  7:58 PM".isReaderNoise())
        assertFalse("Transaction ID:".isReaderNoise())
    }

    @Test
    fun `the screen shows the unknown label for noise and the name otherwise`() {
        assertEquals("Unknown shop", shownShop("msus:quus:óiÜangönms", "Unknown shop"))
        assertEquals("Aloha Coffee", shownShop("Aloha Coffee", "Unknown shop"))
    }
}
