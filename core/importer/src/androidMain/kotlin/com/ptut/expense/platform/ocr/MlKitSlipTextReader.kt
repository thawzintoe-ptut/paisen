package com.ptut.expense.platform.ocr

import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A slip is a screenshot, so it is already the right way up. */
private const val UPRIGHT = 0

/**
 * FR-6 on Android, through ML Kit's bundled Latin model.
 *
 * Bundled and not the Play-services build: NFR-1 says v1 makes no outbound request at all, and the
 * sibling artifact downloads its model on first use. The version catalog says the same thing next
 * to the dependency, and `verifyNoNetworkDependencies` holds the classpath to it.
 *
 * C-3: there is no Thai model, so a Thai merchant line comes back as noise or not at all. That is
 * an outcome the parser is expected to meet, not a failure to hide here.
 *
 * The recognizer is never closed. It is a `single` for the life of the process, and a reader closed
 * after one slip would reload the model for the next one.
 */
class MlKitSlipTextReader : SlipTextReader {

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun read(bytes: ByteArray): List<String> {
        val slip = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } ?: throw IllegalArgumentException("Those bytes are not a picture this phone can decode")

        return recognizer.process(InputImage.fromBitmap(slip, UPRIGHT)).await().linesTopToBottom()
    }
}

/**
 * ML Kit groups lines into blocks and the block order is its own. The parser reads a slip the way
 * a person does — a label, then the value under it — so the lines are put back into the order they
 * sit on the page before it sees them.
 */
private fun Text.linesTopToBottom(): List<String> = textBlocks
    .flatMap { block -> block.lines }
    .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
    .map { it.text }
    .filter { it.isNotBlank() }

/**
 * Hand-rolled rather than `kotlinx-coroutines-play-services`, which would put another Play
 * services artifact on a classpath NFR-1 keeps deliberately short for one extension function.
 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { waiting ->
    addOnSuccessListener { result -> waiting.resume(result) }
    addOnFailureListener { failure -> waiting.resumeWithException(failure) }
    addOnCanceledListener { waiting.cancel() }
}
