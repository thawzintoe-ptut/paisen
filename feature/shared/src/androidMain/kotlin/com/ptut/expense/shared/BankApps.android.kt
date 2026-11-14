package com.ptut.expense.shared

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The Thai bank apps with PromptPay scan-from-photo. Each package is also listed under `<queries>`
 * in the app manifest, or Android 11 and later hide it from [installed].
 */
private val THAI_BANKS = listOf(
    BankApp("com.kasikorn.retail.mbanking.wap", "K PLUS"),
    BankApp("com.scb.phone", "SCB EASY"),
    BankApp("ktbcs.netbank", "Krungthai NEXT"),
    BankApp("com.bbl.mobilebanking", "Bangkok Bank"),
    BankApp("com.krungsri.kma", "KMA Krungsri"),
    BankApp("com.TMBTOUCH.PRODUCTION", "ttb touch"),
    BankApp("com.mobilegsb", "MyMo by GSB"),
)

private class AndroidBankApps(private val context: Context) : BankApps {

    override val canList: Boolean = true

    override fun installed(): List<BankApp> = THAI_BANKS.filter { context.packageManager.getLaunchIntentForPackage(it.id) != null }

    override fun open(app: BankApp) {
        context.packageManager.getLaunchIntentForPackage(app.id)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Android 10 and later write to Pictures/PaiSen with no permission; older ones would need one. */
    override suspend fun savePng(png: ByteArray, name: String): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PaiSen")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        val written = runCatching { resolver.openOutputStream(uri)?.use { it.write(png) } != null }.getOrDefault(false)
        if (!written) {
            resolver.delete(uri, null, null)
            return@withContext false
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        true
    }

    /** Not used: Android lists the bank apps instead. */

    /** Through the app's own FileProvider: the picture sits in the cache and only the chosen app may read it. */
    override fun sharePng(png: ByteArray, text: String?) {
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
actual fun rememberBankApps(): BankApps {
    val context = LocalContext.current
    return remember(context) { AndroidBankApps(context.applicationContext) }
}

actual fun ImageBitmap.encodePng(): ByteArray =
    ByteArrayOutputStream().also { asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }.toByteArray()

/** PNG ignores quality; the API still asks for one. */
private const val PNG_QUALITY = 100

/** Matches the provider in the app manifest and its share_paths.xml. */
private const val SHARE_AUTHORITY = ".share"
private const val SHARE_FILE = "share/paisen-card.png"
