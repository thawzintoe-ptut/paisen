package com.ptut.expense.domain.slip

/**
 * The one thing a screen may ask of the slip source: a queued slip's picture, by the id it was
 * queued under (#118). Listing and deleting pictures stay the importer's.
 */
fun interface SlipPictures {
    /** Null when the picture is gone from the phone. */
    suspend fun bytesOf(id: String): ByteArray?
}
