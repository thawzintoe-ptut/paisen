package com.ptut.expense.domain.today

import com.ptut.expense.domain.HomeTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** FR-19: today as the ledger counts it, in the home zone, never the phone's own. */
class GetHomeTodayUseCase(private val clock: Clock = Clock.System) {
    operator fun invoke(): LocalDate = clock.now().toLocalDateTime(HomeTime).date
}
