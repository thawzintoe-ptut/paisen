package com.ptut.expense.ui

import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.backup.BackupPreview
import com.ptut.expense.domain.backup.BackupProblem
import com.ptut.expense.domain.backup.LedgerBackup
import com.ptut.expense.domain.backup.MergeReport
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.dayAndMonth
import com.ptut.expense.locale.AppText

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
        text.holding(transactions, oldest.dateIn(HomeTime).dayAndMonth())
    } ?: text.fileHoldsNoTransactions
    // #46: said only when the file's home is not this phone's, which is when a restore would take it.
    val fileHome = HomeChoice.of(Home.money.copy(code = homeCurrency), homeZone ?: HomeChoice.BANGKOK)
    val home = fileHome.takeIf { it.money.code != Home.money.code || it.zoneId != Home.choice.value.zoneId }
        ?.let { AppText.copy.phrases.backupCountsIn(it.money.code, text.zonePlace(it.zoneId)) }
    return listOfNotNull(
        rows,
        home,
        groups.takeIf { it > 0 }?.let(AppText.copy.phrases.restoreGroups),
        categories.takeIf { it > 0 }?.let { text.categoryCounts(it, subCategories) },
        shopRules.takeIf { it > 0 }?.let(text.shopRuleCount),
    ).joinToString(" · ")
}

/** The reader's words for [BackupProblem]. Shared by Import my data and first run's RESTORE. */
fun BackupProblem.phrase(): String {
    val text = AppText.copy.phrases
    return when (this) {
        BackupProblem.NotReadable -> text.backupNotReadable

        // 27c: a newer app's file says what to do about it; an older or broken format keeps its number.
        is BackupProblem.OtherVersion -> if (format > LedgerBackup.FORMAT) text.backupNewerVersion else text.backupOtherVersion(format)

        is BackupProblem.OtherHome -> text.backupOtherHome(file, here)

        is BackupProblem.UnknownValue -> text.backupUnknownValue(field, value)
    }
}
