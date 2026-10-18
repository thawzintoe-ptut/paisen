package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.folders.FolderCatalog
import com.ptut.expense.platform.photos.PhotoLibraryFolderCatalog

/** [context] is unused: iOS resolves its own library, so there is no handle to pass across. */
actual fun folderCatalog(context: PtutDatabaseContext): FolderCatalog = PhotoLibraryFolderCatalog()
