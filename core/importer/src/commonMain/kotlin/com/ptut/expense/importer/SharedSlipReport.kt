package com.ptut.expense.importer

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.locale.AppText
import com.ptut.expense.platform.photos.SlipSource
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** A run reads the shared slip last, after a folder batch of up to two hundred pictures. */
private val LANDING_WAIT = 2.minutes

/**
 * Runs [accept] and returns the id of the one picture it added to this inbox, or null when that
 * cannot be told. The share receivers call it while no other share is copying in.
 */
suspend fun SlipSource.pictureAddedBy(accept: suspend () -> Unit): String? {
    val before = runCatching { candidates() }.getOrNull()?.mapTo(HashSet()) { it.id }
    accept()
    val after = runCatching { candidates() }.getOrNull() ?: return null
    return before?.let { known -> after.singleOrNull { it.id !in known }?.id }
}

/**
 * #219: a slip shared in by hand always says what happened to it. "Slip received" at once, then
 * where it went once [scan] has read it: filed to a day, already recorded, waiting in To check, or
 * not read yet. When the picture cannot be told apart, or the run takes longer than [LANDING_WAIT],
 * it says where the slip will show instead (#11).
 */
suspend fun SlipImporter.tellWhereSharedSlipWent(
    inboxId: String?,
    toasts: ToastMessages,
    scan: () -> Unit,
    clock: Clock = Clock.System,
) = coroutineScope {
    val text = AppText.copy.slips
    val lookIn = ToastMessage(text.shareLanded(text.shareLookIn))
    if (inboxId == null) {
        toasts.show(lookIn)
        scan()
        return@coroutineScope
    }
    toasts.show(ToastMessage(text.shareReceived))
    // Listening starts before the scan does, so a fast run cannot land the slip unheard.
    val landing = async(start = CoroutineStart.UNDISPATCHED) {
        withTimeoutOrNull(LANDING_WAIT) { landings.first { it.isFor(inboxId) } }
    }
    scan()
    toasts.show(landing.await()?.sharedMessage(clock) ?: lookIn)
}

/** The importer sees the inbox's id behind the prefix of the source that owns it ("shared:…"). */
private fun SlipLanding.isFor(inboxId: String) = candidateId == inboxId || candidateId.endsWith(":$inboxId")

private fun SlipLanding.sharedMessage(clock: Clock): ToastMessage {
    val text = AppText.copy.slips
    val where = when (place) {
        SlipPlace.Filed -> text.shareFiledTo(occurredAtEpochMillis?.filedDay(clock) ?: text.savedToday)

        SlipPlace.ToCheck -> text.shareWaiting

        SlipPlace.AlreadyRecorded -> text.shareAlreadyRecorded

        SlipPlace.NotReadYet -> text.shareNotReadYet

        // Nothing was recorded, so these take the refusal's tone rather than a tick.
        SlipPlace.NotASlip -> return ToastMessage(text.shareNotASlip, ToastTone.Error)

        SlipPlace.Gone -> return ToastMessage(text.shareGone, ToastTone.Error)
    }
    return ToastMessage(text.shareLanded(where))
}

/** Today lists only today (FR-16), so a slip from another day names the day it went to. */
private fun Long.filedDay(clock: Clock): String {
    val day = dateIn(BangkokTime)
    return if (day == clock.now().toEpochMilliseconds().dateIn(BangkokTime)) AppText.copy.slips.savedToday else day.dayLabel()
}
