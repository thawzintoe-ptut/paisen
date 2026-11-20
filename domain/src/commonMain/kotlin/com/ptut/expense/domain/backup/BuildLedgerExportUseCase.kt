package com.ptut.expense.domain.backup

import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.datetime.LocalDate

/** FR-22's file: its name and what goes in it. */
data class LedgerExport(val fileName: String, val contents: String)

/** The home date in the name, so two exports on the same evening in two zones do not disagree. */
fun ledgerFileName(today: LocalDate): String = "ptut-ledger-$today.json"

/** FR-22 out: the whole ledger as the archive writes it, named for today. Where it is saved is the reader's pick. */
class BuildLedgerExportUseCase(
    private val archive: LedgerArchive,
    private val homeToday: GetHomeTodayUseCase = GetHomeTodayUseCase(),
) {
    suspend operator fun invoke(): LedgerExport = LedgerExport(ledgerFileName(homeToday()), archive.export())
}
