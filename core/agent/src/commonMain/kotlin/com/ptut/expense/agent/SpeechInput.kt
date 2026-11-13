package com.ptut.expense.agent

import com.ptut.expense.locale.AppText

/**
 * #212: the languages Quick add's mic can listen in, by the tag both recognisers take, the short
 * name the switch shows and the full name a sentence uses. Apple has no Burmese locale, so an
 * iPhone never hears [Burmese].
 */
enum class SpeechLanguage(val tag: String, val label: String, val fullName: String) {
    English("en-US", "EN", "English"),
    Thai("th-TH", "ไทย", "ไทย"),
    Burmese("my-MM", "မြန်မာ", "မြန်မာ"),
    ;

    /** Said where this phone has no offline pack for the language. */
    val unavailableNote: String get() = AppText.copy.shell.voiceUnavailable(fullName)

    companion object {
        /** The language a tag such as `th`, `th-TH` or `my_MM` names, or null for one we do not hear. */
        fun of(tag: String): SpeechLanguage? {
            val language = tag.substringBefore('-').substringBefore('_').lowercase()
            return entries.firstOrNull { it.tag.substringBefore('-') == language }
        }

        /**
         * The language an offline pack is for, region included. The mic listens in [tag], so an
         * en-GB pack on the phone cannot answer for en-US.
         */
        fun forPack(tag: String): SpeechLanguage? = entries.firstOrNull { it.tag.equals(tag.replace('_', '-'), ignoreCase = true) }
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

/** How the phone can hear each language: with its own pack, or online through its speech service. */
data class VoiceLanguages(
    /** Packs on the phone now: the mic listens in these with the network off. */
    val heard: Set<SpeechLanguage>,
    /** Packs the phone can download when asked. */
    val downloadable: Set<SpeechLanguage> = emptySet(),
    /** Packs the phone is downloading now. */
    val downloading: Set<SpeechLanguage> = emptySet(),
    /** What the phone's online speech service hears. The voice then goes to that service, usually Google. */
    val online: Set<SpeechLanguage> = emptySet(),
    /** False where the phone has no recogniser at all, so there is nothing to explain. */
    val hasRecogniser: Boolean = true,
)

/** What asking the phone for a pack came to. */
enum class VoiceDownload {
    /** The pack is on the phone. */
    Ready,

    /** The phone took the request and finishes it on its own, often once on Wi-Fi. */
    Started,
    Failed,
}

/**
 * #212: speech to text for Quick add. A language with an offline pack is heard on the phone; one
 * without, on Android, through the phone's online speech service, and the sheet says so. No audio is
 * kept by this app: each platform drops it once the words are read.
 */
interface SpeechInput {
    /** The languages this phone can hear with the network off. Empty where it hears none. */
    suspend fun onDeviceLanguages(): Set<SpeechLanguage>

    /** [onDeviceLanguages] plus the packs the phone could fetch. Only Android 13 and later can say. */
    suspend fun languages(): VoiceLanguages = onDeviceLanguages().let { heard -> VoiceLanguages(heard, hasRecogniser = heard.isNotEmpty()) }

    /** Asks the phone to fetch [language]'s pack. The phone downloads it, so this app makes no request. */
    suspend fun download(language: SpeechLanguage): VoiceDownload = VoiceDownload.Failed

    /** The language chosen last, or else the phone's own when it is one of ours. */
    fun preferredLanguage(): SpeechLanguage

    fun rememberLanguage(language: SpeechLanguage)

    /** Asks for the microphone on first use, then listens until the person stops talking. */
    suspend fun listen(language: SpeechLanguage): Heard

    /** [listen] through the online speech service, for a language with no offline pack. */
    suspend fun listenOnline(language: SpeechLanguage): Heard = listen(language)

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
