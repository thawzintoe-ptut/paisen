package com.ptut.expense.platform.photos

import com.ptut.expense.domain.folders.FolderAccess
import com.ptut.expense.domain.folders.FolderChooser
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.presentLimitedLibraryPickerFromViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIViewController
import kotlin.coroutines.resume

/**
 * The button is chosen from the status (#217), read again each time the screen comes back: ask
 * when never asked, iOS Settings when refused, the picker when some albums are shared, and nothing
 * when every album is listed already.
 */
val iosFolderAccess: FolderAccess get() {
    val text = AppText.copy.folders
    return when (PhotoLibraryAccess().state()) {
        PhotoLibraryStatus.NotAsked -> FolderAccess(action = text.allowAccess, explanation = text.iosNotAsked)
        PhotoLibraryStatus.Refused -> FolderAccess(action = text.openSettings, explanation = text.iosDenied)
        PhotoLibraryStatus.Limited -> FolderAccess(action = text.shareAnotherAlbum, explanation = text.albumsNotShared)
        PhotoLibraryStatus.Full -> FolderAccess(action = null, explanation = text.allFoldersListed)
    }
}

/**
 * The action [iosFolderAccess] named, by the same status. It returns once the reader is back, so
 * the screen re-reads the albums after the dialog or the picker, not before it.
 *
 * `presentLimitedLibraryPicker` and not `PHPickerViewController`: the picker hands back the assets
 * a reader taps and grants no standing access, so an album chosen through it would be read once and
 * never again. This is the one call that widens what the app may keep reading.
 */
class LimitedLibraryFolderChooser(private val access: PhotoLibraryAccess = PhotoLibraryAccess()) : FolderChooser {

    override suspend fun chooseMore(): Unit = when (access.state()) {
        PhotoLibraryStatus.NotAsked -> access.ask()
        PhotoLibraryStatus.Refused -> openSettings()
        PhotoLibraryStatus.Limited -> shareMore()
        PhotoLibraryStatus.Full -> Unit
    }

    private suspend fun shareMore(): Unit = withContext(Dispatchers.Main) {
        val presenting = topViewController() ?: return@withContext
        suspendCancellableCoroutine { closed ->
            PHPhotoLibrary.sharedPhotoLibrary().presentLimitedLibraryPickerFromViewController(presenting) { _ ->
                closed.resume(Unit)
            }
        }
    }

    private suspend fun openSettings(): Unit = withContext(Dispatchers.Main) { openAppSettings() }

    /**
     * The picker needs something to present from, and a scan on foreground return has no screen of
     * its own. Null when the app has no window up, which is when nothing should be presented anyway.
     */
    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        return top
    }
}

/** iOS opens PaiSeN's own page in Settings, one tap above the Photos choice. */
class AppSettingsPhotoSettings : PhotoSettings {

    override val steps: String get() = AppText.copy.home.allowAllIosSteps

    override fun open() = openAppSettings()
}

/** The app's own page in iOS Settings, where Photos is switched back on. Call on the main thread. */
private fun openAppSettings() {
    val page = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(page, options = emptyMap<Any?, Any>(), completionHandler = null)
}
