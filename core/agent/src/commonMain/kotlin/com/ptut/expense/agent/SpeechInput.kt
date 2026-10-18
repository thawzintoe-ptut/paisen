package com.ptut.expense.agent

import com.ptut.expense.locale.AppText

/**
 * #212: the languages Quick add's mic can listen in, by the tag both recognisers take and the name
 * the switch shows. Apple has no Burmese locale, so an iPhone never hears [Burmese].
 */
enum class SpeechLanguage(val tag: String, val label: String) {
    English("en-US", "EN"),
    Thai("th-TH", "ไทย"),
    Burmese("my-MM", "မြန်မာ"),
    ;

    /** Said where this phone has no offline pack for the language. */
    val unavailableNote: String get() = AppText.copy.shell.voiceUnavailable(label)

    companion object {
        /** The language a tag such as `th`, `th-TH` or `my_MM` names, or null for one we do not hear. */
        fun of(tag: String): SpeechLanguage? {
            val language = tag.substringBefore('-').substringBefore('_').lowercase()
            return entries.firstOrNull { it.tag.substringBefore('-') == language }
        }
    }
}

/** What one listen came to. Speech comes back in Unicode, so Burmese words need no Zawgyi pass. */
sealed interface Heard {
    data class Words(val text: String) : Heard

    /** Silence, or the person stopped before saying anything the phone could read. */
    data object Nothing : Heard

    /** The person said no to the microphone, now or earlier. */
    data object NoPermission : Heard

    /** The recogniser failed. [reason] is said to the person as it is. */
    data class Failed(val reason: String) : Heard
}

/**
 * #212: speech to text on the phone only. Neither platform's online service is used, so a language
 * with no offline pack is not offered, and "your spending stays on this phone" (#191) holds for
 * the voice too. No audio is kept: each platform drops it once the words are read.
 */
interface SpeechInput {
    /** The languages this phone can hear with the network off. Empty where it hears none. */
    suspend fun onDeviceLanguages(): Set<SpeechLanguage>

    /** The language chosen last, or else the phone's own when it is one of ours. */
    fun preferredLanguage(): SpeechLanguage

    fun rememberLanguage(language: SpeechLanguage)

    /** Asks for the microphone on first use, then listens until the person stops talking. */
    suspend fun listen(language: SpeechLanguage): Heard

    /** Ends a listen early; it still returns what was heard so far. */
    fun stop()
}

/** A build or a phone with no on-device recogniser. Quick add shows no mic. */
class UnavailableSpeechInput : SpeechInput {
    override suspend fun onDeviceLanguages(): Set<SpeechLanguage> = emptySet()

    override fun preferredLanguage(): SpeechLanguage = SpeechLanguage.English

    override fun rememberLanguage(language: SpeechLanguage) = Unit

    override suspend fun listen(language: SpeechLanguage): Heard = Heard.Nothing

    override fun stop() = Unit
}
