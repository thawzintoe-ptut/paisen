package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.platform.files.DocumentPickerLedgerFileStore
import com.ptut.expense.platform.files.LedgerFileStore

/** iOS carries no context: the picker finds its own screen through `UIApplication`. */
actual fun ledgerFileStore(context: PtutDatabaseContext): LedgerFileStore =
    DocumentPickerLedgerFileStore()
