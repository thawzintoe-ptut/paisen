package com.ptut.expense.platform.photos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * The permission that lets the K PLUS folder be listed.
 *
 * Android 13 split the old storage permission by media type and stopped granting it at all, so
 * the answer is genuinely two answers. It lives in one place because two callers need the same
 * one: the source that fails without it, and the screen that asks for it.
 */
val photoPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/**
 * False under Android 14's "Select photos" as well as under a flat refusal, and that is right:
 * a hand-picked handful of images is not a folder being watched, and a scan over it would report
 * a healthy run while missing every slip the reader did not think to tick.
 */
fun Context.hasPhotoAccess(): Boolean =
    checkSelfPermission(photoPermission) == PackageManager.PERMISSION_GRANTED
