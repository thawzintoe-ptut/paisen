package com.ptut.expense.platform.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The system share sheet with a picture. Here so a second feature can share a card without reaching
 * into the one that built it first (#90, #97 beside #199's "Pay me").
 */
interface ShareSheet {
    /** [text] goes beside the picture where the receiving app takes both. */
    fun sharePng(png: ByteArray, text: String? = null)

    /** Raw bytes as a file, such as a ledger the app could not open. A fake may ignore it. */
    fun shareFile(bytes: ByteArray, fileName: String, mimeType: String) = Unit

    /** Plain text, such as a crash report. A fake may ignore it. */
    fun shareText(text: String) = Unit
}

@Composable
expect fun rememberShareSheet(): ShareSheet

expect fun ImageBitmap.encodePng(): ByteArray

/** A card-sized PNG takes long enough to compress that it leaves the main thread first. */
suspend fun ImageBitmap.encodePngOffMain(): ByteArray = withContext(Dispatchers.Default) { encodePng() }
