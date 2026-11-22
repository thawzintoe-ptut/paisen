package com.ptut.expense.ledger

import com.ptut.expense.domain.LedgerFilter
import com.ptut.expense.locale.AppText

/** The chip's name for a [LedgerFilter], in the reader's language. */
val LedgerFilter.label: String
    get() = when (this) {
        LedgerFilter.All -> AppText.copy.history.filterAll
        LedgerFilter.Spending -> AppText.copy.history.filterSpending
        LedgerFilter.Auto -> AppText.copy.history.filterAuto
        LedgerFilter.ByHand -> AppText.copy.history.filterByHand
        LedgerFilter.Income -> AppText.copy.common.income
        LedgerFilter.Edited -> AppText.copy.history.filterEdited
    }
