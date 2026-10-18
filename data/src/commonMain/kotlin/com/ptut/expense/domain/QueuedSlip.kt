package com.ptut.expense.domain

import com.ptut.expense.domain.slip.SlipField
import kotlinx.datetime.TimeZone

/** Why a slip stopped short of being filed. Each reason asks the reader a different question. */
enum class ReviewReason {
    NewMerchant,
    AmountUnclear,

    /** No shop read, but the text has slip labels (#113). The stored name is read back with `valueOf`. */
    Unreadable,
}

/**
 * A slip the importer read but would not file on its own.
 *
 * [amount] is null when the reader could not make out the number. It stays null rather than
 * carrying a best guess: a pre-filled figure invites a careless confirm, which is the exact
 * failure FR-7 exists to prevent.
 */
data class QueuedSlip(
    val id: String,
    /** Empty when the slip read no shop ([ReviewReason.Unreadable]); screens name it themselves. */
    val merchant: String,
    val occurredAtEpochMillis: Long,
    val note: String,
    val amount: Satang?,
    val reason: ReviewReason,
    /** NFR-4 counts the seven days from when the picture was taken, not from the import. */
    val capturedAtEpochMillis: Long,
    /**
     * Fields the parser produced but had to judge. Empty on most slips, and the confirm screen
     * is built for empty — of the nineteen recorded slips, eight carry no doubt at all.
     */
    val lowConfidence: Set<SlipField> = emptySet(),
    /** The watched folder the slip came from, carried onto the row it becomes (#170). */
    val sourceFolder: String? = null,
)

/** "14 Aug 19:41 · ref 0142K9" — enough for the reader to match the row to a paper slip. */
fun QueuedSlip.metaLine(zone: TimeZone = BangkokTime): String =
    "${occurredAtEpochMillis.dateIn(zone).dayAndMonth()} ${occurredAtEpochMillis.timeOfDayIn(zone)}" +
        note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
