package com.ptut.expense.domain.backup

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.dayAndMonth
import com.ptut.expense.locale.AppText

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

/**
 * The sentence a restore ends on. A file the ledger already held is a success that added nothing,
 * so the count that is not zero leads. Shared by Import my data and first run's RESTORE (#171).
 */
fun MergeReport.phrase(): String {
    val text = AppText.copy.phrases
    val transactions = when {
        added == 0 && alreadyHeld == 0 && groupsAdded > 0 -> null
        added == 0 && alreadyHeld == 0 -> text.restoreEmpty
        added == 0 -> text.restoreNothingNew(alreadyHeld)
        alreadyHeld == 0 -> text.restoreAdded(added)
        else -> "${text.restoreAdded(added)} · ${text.restoreAlreadyHere(alreadyHeld)}"
    }
    // #133: a format 2 file can bring groups back too.
    val groups = if (groupsAdded > 0) text.restoreGroups(groupsAdded) else null
    val restored = when {
        transactions == null -> text.restoreAddedOnly(groups.orEmpty())
        groups == null -> transactions
        else -> "$transactions · $groups"
    }
    // #215: said rather than found later, since a format 1 or 2 file carries no categories.
    return if (lostCategory > 0) "$restored · ${text.restoreLostCategory(lostCategory)}" else restored
}

/**
 * #220: what a chosen file holds, "412 transactions, back to 3 Jun · 2 shared groups", before
 * anything merges. A part at zero is left out. Shared by Import my data and first run's RESTORE.
 */
fun BackupPreview.phrase(): String {
    val text = AppText.copy.settings
    val rows = oldestEpochMillis?.let { oldest ->
        text.holding(transactions, oldest.dateIn(BangkokTime).dayAndMonth())
    } ?: text.fileHoldsNoTransactions
    return listOfNotNull(
        rows,
        groups.takeIf { it > 0 }?.let(AppText.copy.phrases.restoreGroups),
        categories.takeIf { it > 0 }?.let { text.categoryCounts(it, subCategories) },
        shopRules.takeIf { it > 0 }?.let(text.shopRuleCount),
    ).joinToString(" · ")
}

/** A file this build cannot place. Its message is shown to the reader, so it says what is wrong. */
class BackupUnreadable(message: String) : IllegalArgumentException(message)

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
