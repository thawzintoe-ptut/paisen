package com.ptut.expense.domain

import com.ptut.expense.locale.AppText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * NFR-4: a slip that failed keeps its picture for seven days so it can be retried, and loses it
 * after. The shipped default, and still the one number — the sweep, the review note and the
 * settings row all read the reader's chosen [Duration], and this is where an unanswered one starts.
 */
val EvidenceRetention: Duration = 7.days

/**
 * Whole days left before the picture goes, counted from when it was taken.
 *
 * Rounds up, and the reason is which way a reader is misled by the error: "1 day left" on a slip
 * with four hours left is a prompt, while "0 days left" on a slip that still has some is a lie
 * that makes them stop looking. Never negative — a picture already swept has none left, not fewer.
 */
fun evidenceDaysRemaining(
    capturedAtEpochMillis: Long,
    nowEpochMillis: Long,
    retention: Duration = EvidenceRetention,
): Int {
    val elapsed = nowEpochMillis - capturedAtEpochMillis
    val remaining = retention.inWholeMilliseconds - elapsed
    if (remaining <= 0) return 0
    val wholeDay = 1.days.inWholeMilliseconds
    return ((remaining + wholeDay - 1) / wholeDay).toInt()
}

/** How long a kept slip lives, in the words every screen says it in. One place, so none drift. */
fun retentionPhrase(retention: Duration): String = AppText.copy.phrases.days(retention.inWholeDays)
