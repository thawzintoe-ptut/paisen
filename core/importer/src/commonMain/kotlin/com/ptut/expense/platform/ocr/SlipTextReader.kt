package com.ptut.expense.platform.ocr

/**
 * Image bytes to the lines printed on them. ML Kit on Android, Vision on iOS.
 *
 * It reads and nothing else — no parsing, no classification, no storage. That is what lets
 * [com.ptut.expense.domain.slip.SlipParser] be pure and lets a fake feed it a transcript.
 *
 * C-3: the readers ship a Latin model only, so a Thai line comes back as noise or not at all.
 * That is a real outcome, not a failure, and the parser is the one that decides what it means.
 */
interface SlipTextReader {

    /** Empty when nothing legible was found. Throwing is for a reader that could not run at all. */
    suspend fun read(bytes: ByteArray): List<String>
}
