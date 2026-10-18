package com.ptut.expense.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
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
private class IosBankApps : BankApps {

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

    override fun sharePng(png: ByteArray) {
        val sheet = UIActivityViewController(activityItems = listOf(UIImage(data = png.toNSData())), applicationActivities = null)
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        top?.presentViewController(sheet, animated = true, completion = null)
    }
}

@Composable
actual fun rememberBankApps(): BankApps = remember { IosBankApps() }

actual fun ImageBitmap.encodePng(): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
