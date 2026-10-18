package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.folders.FolderAccess
import com.ptut.expense.domain.folders.FolderChooser
import com.ptut.expense.platform.photos.PhotoSettings

/** What this platform can still offer when the folder a reader wants is not in the list. */
expect fun folderAccess(context: PtutDatabaseContext): FolderAccess

expect fun folderChooser(context: PtutDatabaseContext): FolderChooser

expect fun photoSettings(context: PtutDatabaseContext): PhotoSettings
