package com.ptut.expense.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import com.ptut.expense.platform.share.ShareSheet
import com.ptut.expense.platform.share.rememberShareSheet
import com.ptut.expense.platform.toNSData
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import kotlin.coroutines.resume

/**
 * #199 on iOS: Apple lets no app see which other apps are installed, so the QR is saved to Photos
 * and the share sheet offers it to whichever bank app the person uses.
 */
private class IosBankApps(private val share: ShareSheet) : BankApps {

    override val canList: Boolean = false

    override fun installed(): List<BankApp> = emptyList()

    override fun open(app: BankApp) = Unit

    override suspend fun savePng(png: ByteArray, name: String): Boolean {
        val image = UIImage(data = png.toNSData())
        return suspendCancellableCoroutine { continuation ->
            PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                { PHAssetChangeRequest.creationRequestForAssetFromImage(image) },
                completionHandler = { saved, _ -> continuation.resume(saved) },
            )
        }
    }

    /** #90, #97: one share sheet for every card, in :core:platform. */
    override fun sharePng(png: ByteArray, text: String?) = share.sharePng(png, text)
}

@Composable
actual fun rememberBankApps(): BankApps {
    val share = rememberShareSheet()
    return remember(share) { IosBankApps(share) }
}
