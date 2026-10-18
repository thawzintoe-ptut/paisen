package com.ptut.expense.platform.ocr

/** JPEG, PNG and GIF each open with their own few bytes. */
private val LEADING_SIGNATURES = listOf(
    byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
    byteArrayOf(0x47, 0x49, 0x46, 0x38),
)

/** HEIC and AVIF are ISO base media files: a length, then the box type, then the brand. */
private const val ISO_BOX_TYPE_OFFSET = 4
private val ISO_BOX_TYPE = "ftyp".encodeToByteArray()

/** WebP is a RIFF container whose form name sits after the length. */
private const val RIFF_FORM_OFFSET = 8
private val RIFF_HEADER = "RIFF".encodeToByteArray()
private val RIFF_WEBP_FORM = "WEBP".encodeToByteArray()

/**
 * Whether these bytes are a picture rather than the transcript the debug menu injects.
 *
 * Read off the leading bytes and not off a file name, because nothing upstream carries one: the
 * share sheet hands over a content URI and the debug panel hands over a string. Every format below
 * is one a phone camera, a screenshot or a share sheet actually produces — this is a fork in the
 * road, not a validator, and the reader that gets the bytes is the one that says whether they read.
 */
internal fun ByteArray.looksLikeImage(): Boolean =
    LEADING_SIGNATURES.any { startsWith(it, at = 0) } ||
        startsWith(ISO_BOX_TYPE, at = ISO_BOX_TYPE_OFFSET) ||
        (startsWith(RIFF_HEADER, at = 0) && startsWith(RIFF_WEBP_FORM, at = RIFF_FORM_OFFSET))

private fun ByteArray.startsWith(signature: ByteArray, at: Int): Boolean =
    size >= at + signature.size && signature.indices.all { this[at + it] == signature[it] }
