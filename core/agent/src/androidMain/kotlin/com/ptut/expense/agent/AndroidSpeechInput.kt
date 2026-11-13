package com.ptut.expense.agent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.ModelDownloadListener
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.minutes

/**
 * #212 on Android: the on-device recogniser first (Android 12 and later), so a language with a pack
 * stays on the phone. A language with none is heard by the phone's online speech service, usually
 * Google's, and Quick add says so before anyone speaks. Which languages each hears is asked of the
 * phone on Android 13 and later; older phones cannot say.
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

    /** Android 13's pack request, held until it has reached the speech service: see [askForPack]. */
    private var packRequest: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Called by the Activity when the microphone dialog closes. */
    fun settle(granted: Boolean) {
        microphoneAnswer?.complete(granted)
        microphoneAnswer = null
    }

    override suspend fun onDeviceLanguages(): Set<SpeechLanguage> = offlineLanguages().heard

    override suspend fun languages(): VoiceLanguages {
        val offline = offlineLanguages()
        val online = onlineLanguages()
        return offline.copy(online = online, hasRecogniser = offline.hasRecogniser || online.isNotEmpty())
    }

    private suspend fun offlineLanguages(): VoiceLanguages = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> NO_OFFLINE
        !SpeechRecognizer.isOnDeviceRecognitionAvailable(app) -> NO_OFFLINE
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> VoiceLanguages(heard = setOf(SpeechLanguage.English))
        else -> withContext(Dispatchers.Main) { packs() }
    }

    /** Android 12 and older cannot list them, so all three are offered and a miss shows when it listens. */
    private suspend fun onlineLanguages(): Set<SpeechLanguage> = when {
        !SpeechRecognizer.isRecognitionAvailable(app) -> emptySet()
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> SpeechLanguage.entries.toSet()
        else -> withContext(Dispatchers.Main) { onlineSupport() }
    }

    /**
     * A service that cannot say what it hears is treated like an older phone's. Google's answers the
     * check twice on Android 14+, once per API version, so only the first answer counts.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun onlineSupport(): Set<SpeechLanguage> = suspendCancellableCoroutine { done ->
        val checker = SpeechRecognizer.createSpeechRecognizer(app)
        checker.checkRecognitionSupport(
            listenIntent(SpeechLanguage.English, offline = false),
            app.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    checker.destroy()
                    if (done.isActive) done.resume(support.onlineLanguages.mapNotNull(SpeechLanguage::of).toSet())
                }

                override fun onError(error: Int) {
                    checker.destroy()
                    if (done.isActive) done.resume(SpeechLanguage.entries.toSet())
                }
            },
        )
        done.invokeOnCancellation { mainHandler.post { checker.destroy() } }
    }

    override suspend fun download(language: SpeechLanguage): VoiceDownload = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> VoiceDownload.Failed

        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> withContext(Dispatchers.Main) { askForPack(language) }

        // No answer in time is not a failure: the phone carries on and the next open reads it as pending.
        else -> withTimeoutOrNull(PACK_ANSWER_WAIT) { withContext(Dispatchers.Main) { fetchPack(language) } } ?: VoiceDownload.Started
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
        return withContext(Dispatchers.Main) { recognise(language, SpeechRecognizer.createOnDeviceSpeechRecognizer(app), offline = true) }
    }

    override suspend fun listenOnline(language: SpeechLanguage): Heard {
        if (!SpeechRecognizer.isRecognitionAvailable(app)) return Heard.Failed(language.unavailableNote)
        if (!hasMicrophone() && !askForMicrophone()) return Heard.NoPermission
        return withContext(Dispatchers.Main) { recognise(language, SpeechRecognizer.createSpeechRecognizer(app), offline = false) }
    }

    /** A Handler rather than the main executor: online listening runs on Android 8, which has none. */
    override fun stop() {
        mainHandler.post { active?.stopListening() }
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
    private suspend fun packs(): VoiceLanguages = suspendCancellableCoroutine { done ->
        val checker = SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
        checker.checkRecognitionSupport(
            listenIntent(SpeechLanguage.English),
            app.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    checker.destroy()
                    val heard = support.installedOnDeviceLanguages.ours()
                    val downloading = support.pendingOnDeviceLanguages.ours() - heard
                    val downloadable = support.supportedOnDeviceLanguages.ours() - heard - downloading
                    if (done.isActive) done.resume(VoiceLanguages(heard, downloadable, downloading))
                }

                override fun onError(error: Int) {
                    checker.destroy()
                    if (done.isActive) done.resume(VoiceLanguages(heard = emptySet()))
                }
            },
        )
        done.invokeOnCancellation { app.mainExecutor.execute { checker.destroy() } }
    }

    /**
     * Android 13 takes the request and answers nothing. The recogniser queues the request until it
     * connects and destroying it clears that queue, so it is held for [PACK_REQUEST_HOLD_MS] first.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun askForPack(language: SpeechLanguage): VoiceDownload {
        packRequest?.destroy()
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
        packRequest = recognizer
        mainHandler.postDelayed({ releasePackRequest(recognizer) }, PACK_REQUEST_HOLD_MS)
        return runCatching { recognizer.triggerModelDownload(listenIntent(language)) }
            .fold(onSuccess = { VoiceDownload.Started }, onFailure = { VoiceDownload.Failed })
    }

    /** A newer request may have replaced [recognizer]; that one has its own release coming. */
    private fun releasePackRequest(recognizer: SpeechRecognizer) {
        if (packRequest !== recognizer) return
        recognizer.destroy()
        packRequest = null
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun fetchPack(language: SpeechLanguage): VoiceDownload = suspendCancellableCoroutine { done ->
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
        fun finish(result: VoiceDownload) {
            recognizer.destroy()
            if (done.isActive) done.resume(result)
        }
        recognizer.triggerModelDownload(
            listenIntent(language),
            app.mainExecutor,
            object : ModelDownloadListener {
                override fun onProgress(completedPercent: Int) = Unit

                override fun onSuccess() = finish(VoiceDownload.Ready)

                override fun onScheduled() = finish(VoiceDownload.Started)

                override fun onError(error: Int) = finish(VoiceDownload.Failed)
            },
        )
        done.invokeOnCancellation { app.mainExecutor.execute { recognizer.destroy() } }
    }

    private fun List<String>.ours(): Set<SpeechLanguage> = mapNotNull(SpeechLanguage::forPack).toSet()

    /** One listen with [recognizer], on-device or the online service; [offline] says which it was made as. */
    private suspend fun recognise(language: SpeechLanguage, recognizer: SpeechRecognizer, offline: Boolean): Heard = suspendCancellableCoroutine { done ->
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
        recognizer.startListening(listenIntent(language, offline))
        done.invokeOnCancellation { mainHandler.post { release(recognizer) } }
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

        // Only online listening reaches the network, so these mean it had none.
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        -> Heard.Failed(AppText.copy.shell.voiceNeedsInternet(language.fullName))

        else -> Heard.Failed(AppText.copy.shell.couldNotHear)
    }

    private fun listenIntent(language: SpeechLanguage, offline: Boolean = true): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offline)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    private companion object {
        const val CHOICES_FILE = "quick_add_voice"
        const val KEY_LANGUAGE = "language"

        /** Long enough for the queued request to reach the speech service, short enough to let it go. */
        const val PACK_REQUEST_HOLD_MS = 30_000L

        /** A pack is tens of megabytes; past this the sheet says "downloading" and stops waiting. */
        val PACK_ANSWER_WAIT = 2.minutes

        /** No on-device recogniser; the online service may still hear, which [languages] adds. */
        val NO_OFFLINE = VoiceLanguages(heard = emptySet(), hasRecogniser = false)
    }
}
