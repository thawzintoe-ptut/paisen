package com.ptut.expense.settings

import com.ptut.expense.di.AppBuild
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.SettingsCopy

/**
 * The email the Send feedback row opens. It is written in the person's own mail app and they choose
 * to send it, so this app sends nothing.
 */
fun feedbackMail(build: AppBuild, text: SettingsCopy = AppText.copy.settings): String {
    val subject = text.feedbackSubject(build.version).percentEncoded()
    val body = text.feedbackBody(build.version, build.device).percentEncoded()
    return "mailto:$FEEDBACK_ADDRESS?subject=$subject&body=$body"
}

/** RFC 3986: everything but letters, digits and `-._~` goes as UTF-8 percent bytes. */
internal fun String.percentEncoded(): String = encodeToByteArray().joinToString("") { byte ->
    val code = byte.toInt() and BYTE_MASK
    val char = code.toChar()
    if (char in UNRESERVED) char.toString() else "%" + code.toString(HEX).uppercase().padStart(2, '0')
}

private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
private const val BYTE_MASK = 0xFF
private const val HEX = 16
