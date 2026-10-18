package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.platform.files.LedgerFileStore
import com.ptut.expense.platform.files.SafLedgerFileStore

/** `applicationContext`, so the store cannot outlive an Activity while holding one. */
actual fun ledgerFileStore(context: PtutDatabaseContext): LedgerFileStore =
    SafLedgerFileStore(context.applicationContext)
