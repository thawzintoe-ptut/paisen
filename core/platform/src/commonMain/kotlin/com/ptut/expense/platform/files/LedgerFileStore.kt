package com.ptut.expense.platform.files

/**
 * Where an exported ledger lands, and where an imported one is read from. FR-22.
 *
 * A plain interface bound per platform, like [com.ptut.expense.platform.photos.SlipSource] — both
 * ends of this are a system picker, and the picker is the platform-specific part.
 *
 * Both calls return null when the person backed out of that picker. Cancelling is an ordinary
 * answer and not a failure, so it must not be reported as one.
 */
interface LedgerFileStore {

    /** @return where the file landed, for the confirmation line, or null if nothing was written. */
    suspend fun write(suggestedName: String, contents: String): String?

    suspend fun read(): String?
}
