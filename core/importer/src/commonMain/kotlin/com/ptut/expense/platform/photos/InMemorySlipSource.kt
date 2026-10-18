package com.ptut.expense.platform.photos

/**
 * A photo folder that lives in memory, so the whole import pipeline can be driven on a device
 * with no photo library and no OCR model behind it.
 *
 * The twin of [com.ptut.expense.importer.FakeSlipTextReader], and a permanent double rather than
 * scaffolding: an "image" here is a transcript encoded as UTF-8, which is exactly what that reader
 * decodes. Injecting a slip is therefore writing text, not faking a camera.
 *
 * It ships in `commonMain` because both platforms' debug builds bind it, but nothing reaches it in
 * a release build — see the `debug` flag the platform entry point passes to `initKoin`.
 */
class InMemorySlipSource : SlipSource {

    private val images = mutableMapOf<String, Image>()
    private var injected = 0

    private data class Image(val transcript: String, val capturedAtEpochMillis: Long)

    /**
     * Adds one slip and returns the id it was given.
     *
     * The same transcript twice is two images with two ids, and that is the point: F3 tier one
     * hashes the bytes, so the second one has to be recognised as already imported rather than
     * skipped because it happened to reuse a filename.
     */
    fun inject(transcript: String, capturedAtEpochMillis: Long): String {
        val id = "injected-${++injected}"
        images[id] = Image(transcript, capturedAtEpochMillis)
        return id
    }

    override suspend fun candidates(): List<SlipCandidate> = images.entries
        .map { (id, image) -> SlipCandidate(id, image.capturedAtEpochMillis) }
        .sortedBy { it.capturedAtEpochMillis }

    override suspend fun bytesOf(id: String): ByteArray? =
        images[id]?.transcript?.encodeToByteArray()

    override suspend fun delete(id: String) {
        images.remove(id)
    }
}
