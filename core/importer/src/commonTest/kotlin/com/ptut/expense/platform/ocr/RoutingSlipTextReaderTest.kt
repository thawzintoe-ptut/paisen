package com.ptut.expense.platform.ocr

import com.ptut.expense.importer.FakeSlipTextReader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16)
private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val HEIC = "0000".encodeToByteArray() + "ftypheic".encodeToByteArray()
private val WEBP = "RIFF".encodeToByteArray() + "0000".encodeToByteArray() + "WEBP".encodeToByteArray()

/**
 * A debug build binds both readers, so this fork is what decides whether a real slip is read by
 * ML Kit or decoded as UTF-8 and parsed into whatever fell out. Getting it wrong is silent — the
 * importer would file a transaction either way.
 */
class RoutingSlipTextReaderTest {

    private val reader = RoutingSlipTextReader(image = PlatformReader(), transcript = FakeSlipTextReader())

    @Test
    fun `a picture goes to the platform reader`() = runTest {
        assertEquals(listOf("read by the platform"), reader.read(JPEG))
        assertEquals(listOf("read by the platform"), reader.read(PNG))
        assertEquals(listOf("read by the platform"), reader.read(HEIC))
        assertEquals(listOf("read by the platform"), reader.read(WEBP))
    }

    @Test
    fun `a transcript goes to the fake reader`() = runTest {
        val transcript = "Payment Completed\n\n72.00 Baht"

        assertEquals(listOf("Payment Completed", "72.00 Baht"), reader.read(transcript.encodeToByteArray()))
    }

    @Test
    fun `bytes too short to carry a signature read as a transcript`() = runTest {
        assertEquals(listOf("hi"), reader.read("hi".encodeToByteArray()))
    }
}

/** Stands in for ML Kit or Vision. What it returns only has to be recognisable. */
private class PlatformReader : SlipTextReader {
    override suspend fun read(bytes: ByteArray): List<String> = listOf("read by the platform")
}
