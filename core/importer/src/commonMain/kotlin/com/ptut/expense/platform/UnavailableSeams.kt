package com.ptut.expense.platform

import com.ptut.expense.platform.ocr.SlipTextReader
import com.ptut.expense.platform.photos.SlipCandidate
import com.ptut.expense.platform.photos.SlipSource

/**
 * Thrown by a seam no platform has filled in yet. Caught by whatever asked — the importer
 * journals it, a screen shows it as a toast — so the message names the seam and says what that
 * costs, in words a reader can act on rather than a class name.
 */
class SeamUnavailable(seam: String, consequence: String) :
    IllegalStateException("$seam is not wired up on this platform yet — $consequence")

/**
 * The photo library on a platform that has none yet — iOS until #18. Android reads the K PLUS
 * folder and the share inbox from #17, so nothing there resolves to this.
 *
 * It throws rather than returning nothing, and the difference matters: US-8 says a run that
 * scanned and found no slips is a **success**, because it proves the pipe works. A stub that
 * quietly returned an empty list would make every run on such a platform look healthy, which is
 * the exact silent failure the run journal exists to catch. Throwing records a Failure with a reason.
 *
 * [delete] stays a no-op on purpose, so the NFR-4 sweep can run today without the whole app
 * falling over on a picture it was never able to reach.
 */
class UnavailableSlipSource : SlipSource {

    override suspend fun candidates(): List<SlipCandidate> = throw SeamUnavailable("The slip source", "see #18")

    override suspend fun bytesOf(id: String): ByteArray? = null

    override suspend fun delete(id: String) = Unit
}

/** The OCR reader on a platform with none. Android reads with ML Kit from #17; iOS Vision is #18. */
class UnavailableSlipTextReader : SlipTextReader {

    override suspend fun read(bytes: ByteArray): List<String> =
        throw SeamUnavailable("The slip reader", "see #18")
}
