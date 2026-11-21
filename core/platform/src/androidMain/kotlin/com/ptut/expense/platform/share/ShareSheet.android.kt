package com.ptut.expense.platform.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.time.Duration.Companion.minutes

/** Through the app's own FileProvider: the picture sits in the cache and only the chosen app may read it. */
private class AndroidShareSheet(private val context: Context) : ShareSheet {

    override fun sharePng(png: ByteArray, text: String?) {
        deleteSharedPictures(File(context.cacheDir, SHARE_FOLDER), sharedBefore = Long.MAX_VALUE)
        val file = File(context.cacheDir, SHARE_FILE).apply {
            parentFile?.mkdirs()
            writeBytes(png)
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + SHARE_AUTHORITY, file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        text?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
actual fun rememberShareSheet(): ShareSheet {
    val context = LocalContext.current
    // L6: a shared picture can hold a PromptPay QR, so one left from an earlier share goes. A recent
    // one stays, because the app it went to may still be reading it.
    LaunchedEffect(context) {
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - SHARE_KEPT.inWholeMilliseconds
            deleteSharedPictures(File(context.cacheDir, SHARE_FOLDER), sharedBefore = cutoff)
        }
    }
    return remember(context) { AndroidShareSheet(context.applicationContext) }
}

/** Deletes every picture in [folder] last written before [sharedBefore], in epoch milliseconds. */
internal fun deleteSharedPictures(folder: File, sharedBefore: Long) {
    folder.listFiles()?.filter { it.isFile && it.lastModified() < sharedBefore }?.forEach { it.delete() }
}

actual fun ImageBitmap.encodePng(): ByteArray =
    ByteArrayOutputStream().also { asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }.toByteArray()

/** PNG ignores quality; the API still asks for one. */
private const val PNG_QUALITY = 100

/** Matches the provider in the app manifest and its share_paths.xml. */
private const val SHARE_AUTHORITY = ".share"
private const val SHARE_FOLDER = "share"
private const val SHARE_FILE = "$SHARE_FOLDER/paisen-card.png"
private val SHARE_KEPT = 10.minutes
