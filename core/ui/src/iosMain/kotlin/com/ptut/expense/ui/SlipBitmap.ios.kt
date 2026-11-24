package com.ptut.expense.ui

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Full decode: Skia has no sampled decode here, and fitWithin shrinks it after. */
internal actual fun decodeSlipBitmap(bytes: ByteArray, maxSide: Int): ImageBitmap? = bytes.decodeToImageBitmap()
