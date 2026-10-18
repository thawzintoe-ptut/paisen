package com.ptut.expense.platform.photos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.ptut.expense.domain.folders.FolderAccess
import com.ptut.expense.domain.folders.FolderChooser
import com.ptut.expense.locale.AppText

/**
 * With access to all photos, MediaStore already lists every folder holding an image — so the list
 * is the whole answer and a chooser would open a picker with nothing in it the screen does not
 * have. Without that access there is exactly one thing worth offering, and it is not in this app.
 */
fun androidFolderAccess(context: Context): FolderAccess =
    if (context.hasPhotoAccess()) {
        FolderAccess(
            action = null,
            explanation = AppText.copy.folders.allFoldersListed,
        )
    } else {
        FolderAccess(
            action = AppText.copy.folders.allowAccess,
            explanation = AppText.copy.folders.androidNotAllowed,
        )
    }

/** Opens this app's own settings page, which is where the photo permission actually lives. */
class SettingsFolderChooser(context: Context) : FolderChooser {

    private val context = context.applicationContext

    override suspend fun chooseMore() = context.openAppInfo()
}

/** App info is as deep as Android lets an app link; the Photos row is two taps further in. */
class AppInfoPhotoSettings(context: Context) : PhotoSettings {

    private val context = context.applicationContext

    override val steps: String get() = AppText.copy.home.allowAllAndroidSteps

    override fun open() = context.openAppInfo()
}

private fun Context.openAppInfo() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    startActivity(intent)
}
