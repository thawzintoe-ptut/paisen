package com.ptut.expense.domain.slip

import com.ptut.expense.importer.ImportSummary
import com.ptut.expense.importer.SlipImporter

/** One pass of the shipped import pipeline, now: the same hashing, dedupe, parsing and filing a scan runs. */
class RunSlipImportUseCase(private val importer: SlipImporter) {
    suspend operator fun invoke(): ImportSummary = importer.run()
}
