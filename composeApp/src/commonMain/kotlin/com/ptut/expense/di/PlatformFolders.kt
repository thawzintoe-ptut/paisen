package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.folders.FolderCatalog

/**
 * FR-5's platform half. What counts as a folder is not the same on the two systems — a MediaStore
 * bucket on Android, an album on iOS — and only the platform can enumerate its own.
 */
expect fun folderCatalog(context: PtutDatabaseContext): FolderCatalog
