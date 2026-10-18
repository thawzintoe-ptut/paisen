package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.folders.FolderAccess
import com.ptut.expense.domain.folders.FolderChooser
import com.ptut.expense.platform.photos.AppInfoPhotoSettings
import com.ptut.expense.platform.photos.PhotoSettings
import com.ptut.expense.platform.photos.SettingsFolderChooser
import com.ptut.expense.platform.photos.androidFolderAccess

actual fun folderAccess(context: PtutDatabaseContext): FolderAccess =
    androidFolderAccess(context.applicationContext)

actual fun folderChooser(context: PtutDatabaseContext): FolderChooser =
    SettingsFolderChooser(context.applicationContext)

actual fun photoSettings(context: PtutDatabaseContext): PhotoSettings =
    AppInfoPhotoSettings(context.applicationContext)
