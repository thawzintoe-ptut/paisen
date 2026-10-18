package com.ptut.expense.agent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * #212 on Android: the on-device recogniser only (Android 12 and later), never the online service,
 * so the audio stays on the phone. Which languages it hears is asked of the phone on Android 13
 * and later; Android 12 cannot say, so it is offered English alone.
 *
 * The microphone dialog needs an Activity's launcher, so it asks over [asks] and [settle] brings
 * the answer back, the handshake [com.ptut.expense.platform.photos.LauncherPhotoAccessRequest] uses.
 * Every recogniser call runs on the main thread, which `SpeechRecognizer` requires.
 */
class AndroidSpeechInput(context: Context) : SpeechInput {
    private val app = context.applicationContext
    private val choices = app.getSharedPreferences(CHOICES_FILE, Context.MODE_PRIVATE)

    /** Buffered, so an ask is not lost when the Activity has not started collecting yet. */
    private val microphoneAsks = Channel<Unit>(Channel.BUFFERED)
    val asks: Flow<Unit> = microphoneAsks.receiveAsFlow()
    private var microphoneAnswer: CompletableDeferred<Boolean>? = null

    private var active: SpeechRecognizer? = null

    /** Called by the Activity when the microphone dialog closes. */
    fun settle(granted: Boolean) {
        microphoneAnswer?.complete(granted)
        microphoneAnswer = null
    }

    override suspend fun onDeviceLanguages(): Set<SpeechLanguage> = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> emptySet()
        !SpeechRecognizer.isOnDeviceRecognitionAvailable(app) -> emptySet()
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> setOf(SpeechLanguage.English)
        else -> withContext(Dispatchers.Main) { installedLanguages() }
    }

    override fun preferredLanguage(): SpeechLanguage =
        choices.getString(KEY_LANGUAGE, null)?.let { saved -> SpeechLanguage.entries.firstOrNull { it.name == saved } }
            ?: SpeechLanguage.of(Locale.getDefault().toLanguageTag())
            ?: SpeechLanguage.English

    override fun rememberLanguage(language: SpeechLanguage) {
        choices.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    override suspend fun listen(language: SpeechLanguage): Heard {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Heard.Nothing
        if (!hasMicrophone() && !askForMicrophone()) return Heard.NoPermission
        return withContext(Dispatchers.Main) { recognise(language) }
    }

    override fun stop() {
        app.mainExecutor.execute { active?.stopListening() }
    }

    private fun hasMicrophone(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private suspend fun askForMicrophone(): Boolean {
        val pending = CompletableDeferred<Boolean>()
        microphoneAnswer = pending
        microphoneAsks.send(Unit)
        return pending.await()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun installedLanguages(): Set<SpeechLanguage> = suspendCancellableCoroutine { done ->
        val checker = SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
        checker.checkRecognitionSupport(
            listenIntent(SpeechLanguage.English),
            app.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    checker.destroy()
                    done.resume(support.installedOnDeviceLanguages.mapNotNull(SpeechLanguage::of).toSet())
                }

                override fun onError(error: Int) {
                    checker.destroy()
                    done.resume(emptySet())
                }
            },
        )
        done.invokeOnCancellation { app.mainExecutor.execute { checker.destroy() } }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun recognise(language: SpeechLanguage): Heard = suspendCancellableCoroutine { done ->
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
        active = recognizer
        fun finish(heard: Heard) {
            release(recognizer)
            if (done.isActive) done.resume(heard)
        }
        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val words = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    finish(if (words.isNullOrEmpty()) Heard.Nothing else Heard.Words(words))
                }

                override fun onError(error: Int) = finish(heardFrom(error, language))

                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onPartialResults(partialResults: Bundle?) = Unit

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )
        recognizer.startListening(listenIntent(language))
        done.invokeOnCancellation { app.mainExecutor.execute { release(recognizer) } }
    }

    /** Cancel before destroy: a recogniser still listening keeps the microphone open until it times out. */
    private fun release(recognizer: SpeechRecognizer) {
        recognizer.cancel()
        recognizer.destroy()
        if (active === recognizer) active = null
    }

    private fun heardFrom(error: Int, language: SpeechLanguage): Heard = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_CLIENT -> Heard.Nothing

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Heard.NoPermission

        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            Heard.Failed(language.unavailableNote)

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Heard.Failed(AppText.copy.shell.speechBusy)

        else -> Heard.Failed(AppText.copy.shell.couldNotHear)
    }

    private fun listenIntent(language: SpeechLanguage): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    private companion object {
        const val CHOICES_FILE = "quick_add_voice"
        const val KEY_LANGUAGE = "language"
    }
}
