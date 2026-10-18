package com.ptut.expense.agent

import com.ptut.expense.locale.AppText
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages
import kotlin.coroutines.resume

/**
 * #212: what only Swift can do for the mic, handed over at launch the way `IosAuthBridge` is:
 * `SFSpeechRecognizer` with `requiresOnDeviceRecognition`, and the two permission dialogs. Kotlin
 * decides what the words mean.
 *
 * Swift sees each `-> Unit` callback as `-> Void`.
 */
interface IosSpeechBridge {
    /** Which of [tags] this iPhone can hear with the network off. Apple has no Burmese locale. */
    fun onDeviceTags(tags: List<String>): List<String>

    /**
     * Asks for the microphone and speech recognition the first time, then listens in [tag] until
     * the person stops talking. Calls back with the words, with [SPEECH_DENIED] or a reason, or with
     * neither when nothing was said (#216), which reads as [Heard.Nothing].
     */
    fun listen(tag: String, completion: (String?, String?) -> Unit)

    /** Ends a listen early; its completion still runs with what was heard. */
    fun stop()

    /** Drops a listen: the completion runs with no words, and nothing heard is kept. */
    fun cancel()
}

/** What Swift says when the person refused the microphone or speech recognition. */
const val SPEECH_DENIED = "denied"

/** Swift's other failures, sent as codes so the words come from the app's copy in its language (#216). */
const val SPEECH_LANGUAGE_OFFLINE = "language-offline"
const val SPEECH_NO_MICROPHONE = "no-microphone"
const val SPEECH_COULD_NOT_START = "could-not-start"

/** Set by Swift before the first frame. Null in tests and in a build with no bridge. */
object IosSpeechBridges {
    var speech: IosSpeechBridge? = null
}

/** Called from `iOSApp.init`. */
fun installSpeechBridge(bridge: IosSpeechBridge) {
    IosSpeechBridges.speech = bridge
}

/** [SpeechInput] over the bridge. With no bridge it hears nothing, so Quick add shows no mic. */
class IosSpeechInput(private val bridge: () -> IosSpeechBridge? = { IosSpeechBridges.speech }) : SpeechInput {
    private val choices = NSUserDefaults.standardUserDefaults

    override suspend fun onDeviceLanguages(): Set<SpeechLanguage> {
        val speech = bridge() ?: return emptySet()
        return speech.onDeviceTags(SpeechLanguage.entries.map { it.tag }).mapNotNull(SpeechLanguage::of).toSet()
    }

    override fun preferredLanguage(): SpeechLanguage =
        choices.stringForKey(KEY_LANGUAGE)?.let { saved -> SpeechLanguage.entries.firstOrNull { it.name == saved } }
            ?: NSLocale.preferredLanguages.firstNotNullOfOrNull { (it as? String)?.let(SpeechLanguage::of) }
            ?: SpeechLanguage.English

    override fun rememberLanguage(language: SpeechLanguage) {
        choices.setObject(language.name, forKey = KEY_LANGUAGE)
    }

    override suspend fun listen(language: SpeechLanguage): Heard {
        val speech = bridge() ?: return Heard.Nothing
        return suspendCancellableCoroutine { done ->
            speech.listen(language.tag) { words, error ->
                if (done.isActive) done.resume(heardFrom(words, error, language))
            }
            done.invokeOnCancellation { speech.cancel() }
        }
    }

    override fun stop() {
        bridge()?.stop()
    }

    private fun heardFrom(words: String?, error: String?, language: SpeechLanguage): Heard = when (error) {
        SPEECH_DENIED -> Heard.NoPermission

        SPEECH_LANGUAGE_OFFLINE -> Heard.Failed(language.unavailableNote)

        SPEECH_NO_MICROPHONE -> Heard.Failed(AppText.copy.shell.noMicrophone)

        null -> if (words.isNullOrBlank()) Heard.Nothing else Heard.Words(words.trim())

        // Never a raw system sentence: it would be English whatever language the app is in.
        else -> Heard.Failed(AppText.copy.shell.couldNotHear)
    }

    private companion object {
        const val KEY_LANGUAGE = "quick_add_voice_language"
    }
}
