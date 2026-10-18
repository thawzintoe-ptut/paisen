package com.ptut.expense.platform.ocr

import com.ptut.expense.platform.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSError
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate

/** C-3: the Latin reader, named the way Vision names it. There is no Thai model to ask for. */
private const val LATIN = "en-US"

/** One reading per line is all the parser can use; Vision offers alternates it would ignore. */
private const val BEST_CANDIDATE_ONLY = 1uL

/**
 * FR-6 on iOS, through Apple's Vision framework.
 *
 * On device and offline by construction — NFR-1 holds here without a build gate, because Vision
 * ships its models with the OS and makes no request of its own.
 *
 * Language correction is off. It exists to turn near-words into words, and a slip is mostly a
 * reference number, an amount and a merchant name that is not in any dictionary — correction there
 * edits the very characters the parser needs. ML Kit does no correction either, so leaving it off
 * is also what keeps the two platforms reading the same slip the same way.
 *
 * C-3: only the Latin language is asked for, so a Thai merchant line comes back as noise or not at
 * all — the same outcome ML Kit gives on Android, which is the point. Vision does list `th-TH`
 * among its languages, and turning it on here would make one platform read a slip the other cannot.
 *
 * The reading itself cannot be tested off a device: Vision's detectors run in the Simulator without
 * error and return no observations at all, so a round-trip test there would pass an empty list off
 * as "nothing legible". What is testable is the ordering rule, and [topToBottom] is split out for
 * exactly that reason.
 */
class VisionSlipTextReader : SlipTextReader {

    override suspend fun read(bytes: ByteArray): List<String> = withContext(Dispatchers.Default) {
        val request = recognizeText()
        perform(request, bytes)
        request.pageLines().topToBottom()
    }

    private fun recognizeText() = VNRecognizeTextRequest().apply {
        recognitionLevel = VNRequestTextRecognitionLevelAccurate
        usesLanguageCorrection = false
        recognitionLanguages = listOf(LATIN)
    }

    /**
     * Throws rather than answering with no lines. The interface draws that line deliberately: empty
     * means nothing legible was found, and a reader that could not run at all is a different story
     * the importer has to be able to journal.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun perform(request: VNRecognizeTextRequest, bytes: ByteArray) {
        val handler = VNImageRequestHandler(data = bytes.toNSData(), options = emptyMap<Any?, Any?>())

        memScoped {
            val failure = alloc<ObjCObjectVar<NSError?>>()
            if (handler.performRequests(listOf(request), failure.ptr)) return

            val reason = failure.value?.localizedDescription ?: "the reader gave no reason"
            error("Those bytes could not be read as a picture — $reason")
        }
    }
}

/**
 * One line Vision found, and where on the page it sits.
 *
 * Vision's boxes are normalised with the origin at the *bottom* left, so [top] is the largest value
 * at the top of the page. Naming it after what a reader sees rather than after the coordinate space
 * is what keeps the sort below readable.
 */
internal data class PageLine(val text: String, val top: Double, val left: Double)

/**
 * The parser reads a slip the way a person does — a label, then the value under it — so the lines
 * are put back into the order they sit on the page before it sees them. Vision returns its
 * observations in an order of its own.
 *
 * Descending by [PageLine.top] because of where Vision puts the origin; see the note there.
 */
internal fun List<PageLine>.topToBottom(): List<String> =
    sortedWith(compareByDescending<PageLine> { it.top }.thenBy { it.left })
        .map { it.text }
        .filter { it.isNotBlank() }

@OptIn(ExperimentalForeignApi::class)
private fun VNRecognizeTextRequest.pageLines(): List<PageLine> = results
    .orEmpty()
    .filterIsInstance<VNRecognizedTextObservation>()
    .mapNotNull { observation ->
        val text = observation.topCandidates(BEST_CANDIDATE_ONLY)
            .filterIsInstance<VNRecognizedText>()
            .firstOrNull()
            ?.string
            ?: return@mapNotNull null

        observation.boundingBox.useContents {
            PageLine(text = text, top = origin.y + size.height, left = origin.x)
        }
    }
