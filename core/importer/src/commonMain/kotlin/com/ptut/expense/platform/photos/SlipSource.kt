package com.ptut.expense.platform.photos

/**
 * One image that might be a slip. The bytes are deliberately absent: a folder can hold hundreds,
 * and a list that carried every image would be the pipeline's memory ceiling.
 *
 * [capturedAtEpochMillis] is the photo's own timestamp, not the import's. NFR-4 sweeps a failed
 * slip's image seven days after it was taken, so the age has to come from the picture.
 */
data class SlipCandidate(
    val id: String,
    val capturedAtEpochMillis: Long,
    /**
     * Which watched folder listed this, so a read can be counted against it. Null for a slip that
     * came from no folder at all — the share sheet's inbox — which is why the badge's total and
     * the ledger's slip count are not the same number and never claim to be.
     */
    val folderId: String? = null,
    /** The watched folder's name, so a filed row can say where it came from (#170). */
    val folderName: String? = null,
)

/**
 * Where slip images come from — the K PLUS folder on Android, the photo library on iOS.
 *
 * An interface rather than an `expect`, because the importer's rules are the risky part and they
 * have to run in the host suite. An `expect` binds at compile time and admits exactly one actual
 * per target, so a fake could never stand in for it.
 */
interface SlipSource {

    /** Oldest first, so a run that stops early has still made progress in order. */
    suspend fun candidates(): List<SlipCandidate>

    /** Null when the image has gone since it was listed — a user can delete one mid-run. */
    suspend fun bytesOf(id: String): ByteArray?

    /** NFR-4. Deleting one that is already gone is not an error. */
    suspend fun delete(id: String)
}
