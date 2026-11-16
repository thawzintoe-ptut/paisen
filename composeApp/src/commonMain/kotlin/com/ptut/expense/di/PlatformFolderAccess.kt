package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.folders.FolderAccess
import com.ptut.expense.domain.folders.FolderChooser
import com.ptut.expense.platform.photos.PhotoAccessRequest
import com.ptut.expense.platform.photos.PhotoSettings

/** What this platform can still offer when the folder a reader wants is not in the list. */
expect fun folderAccess(context: PtutDatabaseContext): FolderAccess

/** [request] is the dialog Android raises again to pick more photos. */
expect fun folderChooser(context: PtutDatabaseContext, request: PhotoAccessRequest): FolderChooser

expect fun photoSettings(context: PtutDatabaseContext): PhotoSettings
