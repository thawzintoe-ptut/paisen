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
}

@Composable
expect fun rememberShareSheet(): ShareSheet

expect fun ImageBitmap.encodePng(): ByteArray

/** A card-sized PNG takes long enough to compress that it leaves the main thread first. */
suspend fun ImageBitmap.encodePngOffMain(): ByteArray = withContext(Dispatchers.Default) { encodePng() }
