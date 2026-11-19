package com.ptut.expense.domain.backup

/**
 * What an import did, so the toast can say it. The two counts are separate on purpose: importing
 * a file the ledger already holds is a success that adds nothing, and reporting it as "0 added"
 * alone reads as a failure.
 */
data class MergeReport(
    val added: Int,
    val alreadyHeld: Int,
    val groupsAdded: Int = 0,
    /** #215: rows added without the category the file named, because this phone has no such category. */
    val lostCategory: Int = 0,
)

/** Why a file was refused, worded by the screen that shows it. */
sealed interface BackupProblem {
    /** Not JSON, or not a ledger file, or a value that cannot be stored as it stands. */
    data object NotReadable : BackupProblem

    /** Written by a format this build does not read. */
    data class OtherVersion(val format: Int) : BackupProblem

    /** #46: the file counts in [file], and a ledger with rows here counts in [here]. */
    data class OtherHome(val file: String, val here: String) : BackupProblem

    /** A [field] holding a [value] no build writes, cut short: a file's text is not this app's to print in full. */
    data class UnknownValue(val field: String, val value: String) : BackupProblem
}

/** A file this build cannot place, and [problem] says why. The message is for logs, never the screen. */
class BackupUnreadable(val problem: BackupProblem) : IllegalArgumentException(problem.toString())

/**
 * FR-22, both directions. The ledger as text, and text back into the ledger.
 *
 * [restore] merges and never replaces. A restore is somebody asking for rows back, not asking for
 * the rows they have to go away — and a destructive import is unrecoverable in an app whose whole
 * promise is that nothing leaves the device.
 */
interface LedgerArchive {

    suspend fun export(): String

    /**
     * #220: what [json] holds, read and checked as [restore] would read it, with nothing written.
     *
     * @throws BackupUnreadable when the text is not a ledger file this build can place.
     */
    suspend fun preview(json: String): BackupPreview = LedgerBackup.decode(json).preview()

    /** @throws BackupUnreadable when the text is not a ledger file this build can place. */
    suspend fun restore(json: String): MergeReport
}
