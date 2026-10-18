package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.folders.FolderAccess
import com.ptut.expense.domain.folders.FolderChooser
import com.ptut.expense.platform.photos.AppSettingsPhotoSettings
import com.ptut.expense.platform.photos.LimitedLibraryFolderChooser
import com.ptut.expense.platform.photos.PhotoSettings
import com.ptut.expense.platform.photos.iosFolderAccess

/** [context] is unused: iOS resolves its own library, so there is no handle to pass across. */
actual fun folderAccess(context: PtutDatabaseContext): FolderAccess = iosFolderAccess

actual fun folderChooser(context: PtutDatabaseContext): FolderChooser = LimitedLibraryFolderChooser()

actual fun photoSettings(context: PtutDatabaseContext): PhotoSettings = AppSettingsPhotoSettings()
