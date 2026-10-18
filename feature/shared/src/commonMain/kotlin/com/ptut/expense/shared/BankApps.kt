package com.ptut.expense.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.ptut.expense.design.QrPaper

/** A Thai bank app this phone can open, by its package name on Android. */
data class BankApp(val id: String, val name: String)

/**
 * #199's "Open my bank app". No link opens every Thai bank app with a PromptPay QR, so the QR is
 * saved to Photos and the person picks it with the bank app's own "scan from photo". Android can
 * see which bank apps are installed and opens one; iOS cannot, and shows the share sheet instead.
 */
interface BankApps {
    /** True where the phone can tell which bank apps are installed. */
    val canList: Boolean

    fun installed(): List<BankApp>

    fun open(app: BankApp)

    /** Saves [png] to the photo library; false where this phone can't without a new permission. */
    suspend fun savePng(png: ByteArray, name: String): Boolean

    /** The system share sheet with the QR image, where [canList] is false. */
    fun sharePng(png: ByteArray)
}

@Composable
expect fun rememberBankApps(): BankApps

expect fun ImageBitmap.encodePng(): ByteArray

/**
 * The QR as a PNG a bank app can read from a photo: dark modules on white with a quiet border.
 * A transparent background would read as black and the code would not scan.
 */
fun qrPng(qr: Painter, density: Density): ByteArray {
    val bitmap = ImageBitmap(PNG_SIZE, PNG_SIZE)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(PNG_SIZE_PX, PNG_SIZE_PX)) {
        drawRect(QrPaper)
        inset(QUIET_ZONE_PX) { with(qr) { draw(size) } }
    }
    return bitmap.encodePng()
}

private const val PNG_SIZE = 1024
private const val PNG_SIZE_PX = 1024f
private const val QUIET_ZONE_PX = 64f
