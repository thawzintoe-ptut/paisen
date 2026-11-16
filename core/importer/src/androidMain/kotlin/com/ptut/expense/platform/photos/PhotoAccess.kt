package com.ptut.expense.platform.photos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** How much of the gallery Android lets this app list. */
enum class PhotoAccessLevel {
    /** Every photo, so a watched folder shows each new slip on its own. */
    All,

    /**
     * Android 14's "Select photos": only the photos the reader ticked. They are read, but a slip a
     * bank app saves later stays hidden until it is picked too, so the screens say so.
     */
    Picked,

    None,
}

/**
 * The permissions the dialog asks for. Android 13 split the old storage permission by media type
 * and stopped granting it at all, and Android 14 added "Select photos" as a permission of its own.
 * Asking for both on 14 is what makes the dialog offer it as a real choice and not a session grant.
 */
val photoPermissions: Array<String>
    get() = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)

        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

/** Read fresh on every call: the reader can change it in Settings while the app is open. */
fun Context.photoAccessLevel(): PhotoAccessLevel {
    val all = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return when {
        isGranted(all) -> PhotoAccessLevel.All

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PhotoAccessLevel.Picked

        else -> PhotoAccessLevel.None
    }
}

private fun Context.isGranted(permission: String) =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
