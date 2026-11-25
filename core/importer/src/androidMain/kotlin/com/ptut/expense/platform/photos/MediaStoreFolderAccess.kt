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
 * have. Under "Select photos" it lists only the picks, and Android's own picker adds more. Without
 * access there is exactly one thing worth offering, and it is not in this app.
 */
fun androidFolderAccess(context: Context): FolderAccess {
    val text = AppText.copy.folders
    return when (context.photoAccessLevel()) {
        PhotoAccessLevel.All -> FolderAccess(action = null, explanation = text.allFoldersListed)
        PhotoAccessLevel.Picked -> FolderAccess(action = text.pickMorePhotos, explanation = text.androidOnlyPicked)
        PhotoAccessLevel.None -> FolderAccess(action = text.allowAccess, explanation = text.androidNotAllowed)
    }
}

/**
 * The action [androidFolderAccess] named. Under "Select photos" asking again opens Android's picker,
 * which also offers "Allow all". Otherwise this app's own settings page, where the permission lives.
 */
class SettingsFolderChooser(context: Context, private val request: PhotoAccessRequest) : FolderChooser {

    private val context = context.applicationContext

    override suspend fun chooseMore() = when (context.photoAccessLevel()) {
        PhotoAccessLevel.Picked -> request.ask()
        PhotoAccessLevel.All, PhotoAccessLevel.None -> context.openAppInfo()
    }
}

/** App info is as deep as Android lets an app link; the Photos row is two taps further in. */
class AppInfoPhotoSettings(context: Context) : PhotoSettings {

    private val context = context.applicationContext

    override val steps: String get() = AppText.copy.home.allowAllAndroidSteps

    override val onlyPickedPhotos: Boolean get() = context.photoAccessLevel() == PhotoAccessLevel.Picked

    override val photosAllowed: Boolean get() = context.photoAccessLevel() != PhotoAccessLevel.None

    override fun open() = context.openAppInfo()
}

private fun Context.openAppInfo() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    startActivity(intent)
}
