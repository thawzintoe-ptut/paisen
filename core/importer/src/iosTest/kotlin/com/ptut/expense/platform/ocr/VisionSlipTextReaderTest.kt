package com.ptut.expense.platform.ocr

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * What can honestly be held to on a simulator.
 *
 * Vision's own reading cannot: its detectors run there without error and return no observations at
 * all, so a slip rendered and read back would come out empty and a test asserting that would pass
 * whether or not the reader worked. Reading real text is proved on a device, in the same run that
 * proves the album scan.
 *
 * What is left is the part written here rather than by Apple — the order the lines arrive in, and
 * the difference between "found nothing" and "could not run".
 */
class VisionSlipTextReaderTest {

    @Test
    fun `lines arrive in the order they sit on the page`() {
        val page = listOf(
            PageLine(text = "THB 250.00", top = 0.40, left = 0.10),
            PageLine(text = "BIG C EXTRA", top = 0.80, left = 0.10),
            PageLine(text = "Ref 12", top = 0.20, left = 0.10),
        )

        assertEquals(listOf("BIG C EXTRA", "THB 250.00", "Ref 12"), page.topToBottom())
    }

    /** Two readings on one line are read left to right, the way the line was printed. */
    @Test
    fun `two lines at the same height are read left to right`() {
        val page = listOf(
            PageLine(text = "250.00", top = 0.40, left = 0.70),
            PageLine(text = "Amount", top = 0.40, left = 0.10),
        )

        assertEquals(listOf("Amount", "250.00"), page.topToBottom())
    }

    /** Vision hands back the odd empty box, and a blank line would shift every index the parser counts. */
    @Test
    fun `a box Vision read nothing out of is dropped`() {
        val page = listOf(
            PageLine(text = "BIG C", top = 0.80, left = 0.10),
            PageLine(text = "   ", top = 0.60, left = 0.10),
        )

        assertEquals(listOf("BIG C"), page.topToBottom())
    }

    @Test
    fun `an empty page reads as no lines`() {
        assertEquals(emptyList(), emptyList<PageLine>().topToBottom())
    }

    /**
     * The other half of the interface's contract: empty means nothing legible was found, and a
     * reader that could not run at all has to throw instead so the importer can journal why.
     */
    @Test
    fun `bytes that are not a picture fail with a reason`() = runTest {
        val failure = assertFails { VisionSlipTextReader().read("not a picture".encodeToByteArray()) }

        assertTrue(failure.message.orEmpty().contains("could not be read"), failure.message.orEmpty())
    }
}
