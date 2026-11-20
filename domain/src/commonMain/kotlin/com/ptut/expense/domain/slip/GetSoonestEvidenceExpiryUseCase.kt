package com.ptut.expense.domain.slip

import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.evidenceDaysRemaining
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * NFR-4: the days left before the oldest waiting slip loses its picture, or null with none waiting.
 * The soonest is the deadline the reader has; the newest slip's would promise time the oldest lacks.
 */
class GetSoonestEvidenceExpiryUseCase(private val clock: Clock = Clock.System) {
    operator fun invoke(slips: List<QueuedSlip>, retention: Duration): Int? {
        val now = clock.now().toEpochMilliseconds()
        return slips.minOfOrNull { evidenceDaysRemaining(it.capturedAtEpochMillis, now, retention) }
    }
}
