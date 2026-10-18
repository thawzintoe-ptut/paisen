package com.ptut.expense.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * #190: invite links and codes. A link is `https://<linkDomain>/join/<code>`; the same nine
 * characters can be typed by hand, so the alphabet leaves out 0/O, 1/I/L, which read alike.
 *
 * A link can arrive before the app has drawn anything (Android's intent, iOS's onOpenURL), so it
 * waits here until the shell is ready to open the join screen.
 */
object InviteLinks {
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    const val CODE_LENGTH = 9

    private val pending = MutableStateFlow<String?>(null)
    val pendingCode: StateFlow<String?> = pending

    /** A link or a typed code. Anything that holds no code is ignored. */
    fun offer(text: String) {
        codeIn(text)?.let { pending.value = it }
    }

    fun consume() {
        pending.value = null
    }

    /** The code in a link or in what someone typed, upper-cased and without spaces or dashes. */
    fun codeIn(text: String): String? {
        val tail = text.trim().substringAfter("/join/", text.trim()).substringBefore('?').substringBefore('/')
        val code = tail.uppercase().filter { it in ALPHABET }
        return code.takeIf { it.length == CODE_LENGTH }
    }

    fun link(code: String): String = "https://${FirebaseConfig.LINK_DOMAIN}/join/$code"

    /**
     * Uuid.random() is the platform's secure random source. Bytes 6 and 8 carry the UUID's fixed
     * version and variant bits, so they are skipped; a byte of 248 or more is dropped so each of the
     * 31 characters is equally likely.
     */
    @OptIn(ExperimentalUuidApi::class)
    internal fun newCode(): String = buildString {
        while (length < CODE_LENGTH) {
            Uuid.random().toByteArray().forEachIndexed { index, byte ->
                val value = byte.toInt() and BYTE_MASK
                if (length < CODE_LENGTH && index !in FIXED_UUID_BYTES && value < UNBIASED_LIMIT) append(ALPHABET[value % ALPHABET.length])
            }
        }
    }

    private const val BYTE_MASK = 0xFF
    private val FIXED_UUID_BYTES = setOf(6, 8)

    /** 248 is the largest multiple of 31 below 256. */
    private const val UNBIASED_LIMIT = 248
}
