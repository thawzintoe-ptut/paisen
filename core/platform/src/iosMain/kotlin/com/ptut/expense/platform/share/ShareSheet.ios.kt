package com.ptut.expense.platform.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import com.ptut.expense.platform.toNSData
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage

/** The share sheet over whatever is on screen, so it shows above a bottom sheet as well. */
private class IosShareSheet : ShareSheet {
    override fun sharePng(png: ByteArray, text: String?) {
        val sheet = UIActivityViewController(activityItems = listOfNotNull(UIImage(data = png.toNSData()), text), applicationActivities = null)
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        top?.presentViewController(sheet, animated = true, completion = null)
    }
}

@Composable
actual fun rememberShareSheet(): ShareSheet = remember { IosShareSheet() }

actual fun ImageBitmap.encodePng(): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
