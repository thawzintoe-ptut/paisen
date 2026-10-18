package com.ptut.expense.agent

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * #205: one shape for a sentence before the grammar or a model reads it. Zawgyi becomes Unicode,
 * Burmese ၀–၉ and Thai ๐–๙ become 0–9, and the four ways of writing baht go. Case stays, so a note
 * keeps the capitals it was typed with; the grammar matches without case.
 *
 * [zawgyi] is null where the model could not be read; such text is then taken as Unicode.
 */
class TextNormaliser(private val zawgyi: ZawgyiDetector?) {

    fun normalise(text: String): String {
        val unicode = if (isZawgyi(text)) ZawgyiConverter.toUnicode(text) else text
        val digits = buildString(unicode.length) {
            unicode.forEach { char -> append(asciiDigit(char) ?: char) }
        }
        return digits
            .replace(CURRENCY, " ")
            .replace(INVISIBLE, "")
            .replace(BURMESE_PUNCTUATION, " ")
            .replace(SPACES, " ")
            .trim()
    }

    /** 0.95: myanmar-tools' advice when converting without asking, so Unicode is rarely flagged. */
    fun isZawgyi(text: String): Boolean = (zawgyi?.zawgyiProbability(text) ?: 0.0) > ZAWGYI_THRESHOLD

    private fun asciiDigit(char: Char): Char? = when (char) {
        in MYANMAR_ZERO..MYANMAR_NINE -> '0' + (char - MYANMAR_ZERO)
        in THAI_ZERO..THAI_NINE -> '0' + (char - THAI_ZERO)
        else -> null
    }

    private companion object {
        const val ZAWGYI_THRESHOLD = 0.95
        const val MYANMAR_ZERO = '၀'
        const val MYANMAR_NINE = '၉'
        const val THAI_ZERO = '๐'
        const val THAI_NINE = '๙'

        // ဘတ် not followed by စ်, because ဘတ်စ်ကား is a bus.
        val CURRENCY = Regex("""฿|บาท|ဘတ်(?!စ်)|\bbaht\b""", RegexOption.IGNORE_CASE)
        val INVISIBLE = Regex("[​‌‍﻿]")
        val BURMESE_PUNCTUATION = Regex("[၊။]")
        val SPACES = Regex("\\s+")
    }
}

/**
 * The model file is 28 KB, so it is read on first use rather than at launch, and once. Siri and the
 * Add sheet can both ask at the same moment, hence the lock.
 */
class NormaliserSource(private val readModel: suspend () -> ByteArray?) {
    private val lock = Mutex()
    private var loaded: TextNormaliser? = null

    suspend fun get(): TextNormaliser = lock.withLock {
        loaded ?: TextNormaliser(readModel()?.let(::ZawgyiDetector)).also { loaded = it }
    }
}

/** Where `composeResources` keeps the model myanmar-tools ships. */
const val ZAWGYI_MODEL_FILE = "files/zawgyi_unicode_model.dat"
