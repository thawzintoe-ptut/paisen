package com.ptut.expense.platform.photos

import com.ptut.expense.locale.AppText
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHPhotoLibrary
import kotlin.coroutines.resume

/**
 * Thrown rather than answered with an empty list, exactly as the Android folder scan does. US-8
 * counts a scan that found no slips as a success, so a silent empty answer would leave the banner
 * green while nothing was being watched. The importer journals this message, so it is written for
 * the person reading it.
 */
class PhotoAccessDenied : IllegalStateException(AppText.copy.folders.iosAccessDenied)

/** iOS's answer, folded to what Photo folders offers next (#217). */
enum class PhotoLibraryStatus {
    /** Never asked: the button asks. */
    NotAsked,

    /** Denied, or Restricted by Screen Time: only iOS Settings can change it, so the button goes there. */
    Refused,

    /** Some albums shared: they are read, and the button shares another. */
    Limited,

    /** Every album is listed, so there is nothing more to offer. */
    Full,
}

/**
 * Whether the album may be listed, and the one place that asks. It answers [PhotoAccessRequest] so
 * that first run's button raises this dialog and not a second copy of the same code.
 *
 * Read-write and not add-only: this app never writes a photo, but add-only carries no permission
 * to read one, and reading is the whole job.
 *
 * "Limited" counts as allowed for the albums the reader shared (#217). It used to count as denied,
 * like Android 14's "Select photos", so a scan could not report a healthy run over a handful of
 * images. But it left a reader who chose Limited with no way forward. Photo folders now lists what
 * was shared and offers the limited-library picker to share more.
 *
 * Stateless on purpose: every call reads `PHPhotoLibrary`, so the copy the slip source gates on and
 * the copy the graph binds cannot disagree.
 */
class PhotoLibraryAccess : PhotoAccessRequest {

    fun isGranted(): Boolean = when (state()) {
        PhotoLibraryStatus.Full, PhotoLibraryStatus.Limited -> true
        PhotoLibraryStatus.NotAsked, PhotoLibraryStatus.Refused -> false
    }

    fun state(): PhotoLibraryStatus = when (status()) {
        PHAuthorizationStatusAuthorized -> PhotoLibraryStatus.Full
        PHAuthorizationStatusLimited -> PhotoLibraryStatus.Limited
        PHAuthorizationStatusNotDetermined -> PhotoLibraryStatus.NotAsked
        else -> PhotoLibraryStatus.Refused
    }

    /**
     * Shows the system dialog the first time and does nothing afterwards.
     *
     * A refusal is not raised here. The scan that follows fails on its own and journals why, which
     * is what puts the reason on the Today banner and in Settings instead of in a dialog that is
     * already gone.
     */
    override suspend fun ask() {
        if (status() != PHAuthorizationStatusNotDetermined) return

        suspendCancellableCoroutine { waiting ->
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) {
                waiting.resume(Unit)
            }
        }
    }

    private fun status() = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
}
