package com.ptut.expense.domain

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class QueuedSlipTest {

    private val bangkok = TimeZone.of("Asia/Bangkok")

    @Test
    fun datesTheSlipAndRepeatsWhateverTheReaderNeedsToRecogniseIt() {
        val slip = QueuedSlip(
            id = "q1",
            merchant = "MK Restaurant Rama 9",
            occurredAtEpochMillis = LocalDateTime(2026, 8, 14, 19, 41)
                .toInstant(bangkok).toEpochMilliseconds(),
            note = "ref 0142K9",
            amount = Satang(48_500),
            reason = ReviewReason.NewMerchant,
            capturedAtEpochMillis = 0L,
        )

        assertEquals("14 Aug 19:41 · ref 0142K9", slip.metaLine(bangkok))
    }
}
