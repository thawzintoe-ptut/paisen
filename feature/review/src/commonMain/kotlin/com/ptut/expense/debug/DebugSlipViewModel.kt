package com.ptut.expense.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.importer.SlipImporter
import com.ptut.expense.importer.SlipTranscripts
import com.ptut.expense.platform.photos.InMemorySlipSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Drives the real import pipeline from a device, with no photo library and no OCR model.
 *
 * It injects a transcript and runs the importer that ships — the same hashing, dedupe, parsing,
 * classification and journalling a real slip goes through. Only the two platform seams are
 * doubles, which is the point: the rules under test are the shipped ones.
 */
class DebugSlipViewModel(
    private val source: InMemorySlipSource,
    private val importer: SlipImporter,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val lastRun = MutableStateFlow("Nothing injected yet.")

    /** What the last run did, in one line. Empty until something has been injected. */
    val summary: StateFlow<String> = lastRun.asStateFlow()

    fun injectReadable() = inject(SlipTranscripts.readable)

    fun injectSameMerchant() = inject(SlipTranscripts.readableSameMerchant)

    fun injectUnreadable() = inject(SlipTranscripts.unreadable)

    private fun inject(transcript: String) {
        viewModelScope.launch {
            source.inject(transcript, clock.now().toEpochMilliseconds())
            val run = importer.run()
            lastRun.value = "found ${run.found} · filed ${run.filed} · queued ${run.queued} · " +
                "already seen ${run.alreadySeen} · unreadable ${run.unreadable}"
        }
    }
}
