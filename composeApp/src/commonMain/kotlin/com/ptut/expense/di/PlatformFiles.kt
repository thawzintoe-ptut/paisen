package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.platform.files.LedgerFileStore

/**
 * FR-22's platform half. Both ends of an export are a system picker, and a picker is the one
 * thing common code cannot draw.
 *
 * It takes the platform handle the graph already carries rather than a second one: on Android
 * that type is the `Context` the file store needs, and on iOS it is an object with nothing on it.
 */
expect fun ledgerFileStore(context: PtutDatabaseContext): LedgerFileStore
