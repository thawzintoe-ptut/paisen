package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.folders.FolderCatalog
import com.ptut.expense.platform.photos.MediaStoreFolderCatalog

actual fun folderCatalog(context: PtutDatabaseContext): FolderCatalog =
    MediaStoreFolderCatalog(context.applicationContext)
