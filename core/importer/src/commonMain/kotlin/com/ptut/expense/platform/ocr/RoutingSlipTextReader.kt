package com.ptut.expense.platform.ocr

/**
 * Sends a picture to the platform's reader and a transcript to the fake one.
 *
 * A debug build wants both. It runs on a real phone against real slips, and it also has to be
 * drivable from the debug menu on a machine with no photo library — which is what
 * [com.ptut.expense.importer.FakeSlipTextReader] is for. Binding one of the two would cost the
 * other, and [SlipTextReader.read] is handed bytes and nothing else, so the bytes are what the
 * choice has to be made on.
 *
 * It is only built where both halves exist. Which reader a platform has is the graph's question,
 * not this one's.
 */
class RoutingSlipTextReader(
    private val image: SlipTextReader,
    private val transcript: SlipTextReader,
) : SlipTextReader {

    override suspend fun read(bytes: ByteArray): List<String> =
        if (bytes.looksLikeImage()) image.read(bytes) else transcript.read(bytes)
}
