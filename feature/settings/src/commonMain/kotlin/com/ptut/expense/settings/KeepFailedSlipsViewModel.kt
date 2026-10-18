package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.QueuedSlip
import com.ptut.expense.domain.evidenceDaysRemaining
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.retentionPhrase
import com.ptut.expense.domain.settings.ImportSettings
import com.ptut.expense.domain.settings.RetentionChoices
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.Duration

data class RetentionOptionUiModel(val days: Int, val label: String, val chosen: Boolean)

data class KeepFailedSlipsUiState(
    val options: List<RetentionOptionUiModel>,
    /** What is being kept right now, so the choice is made against a real number and not a rule. */
    val kept: String,
    /** The deadline the reader actually has, or null when nothing is waiting. */
    val deadline: String?,
)

/**
 * NFR-4: how long a slip the reader has not confirmed keeps its picture.
 *
 * A shortening takes effect on the next sweep rather than here. The sweep is the one thing that
 * deletes a picture — [com.ptut.expense.importer.SlipImporter] owns a slip image's whole life —
 * and a second deleter is how an orphaned image outlives the row that explained it.
 */
class KeepFailedSlipsViewModel(
    ledger: LedgerRepository,
    private val settings: ImportSettings,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    val uiState: StateFlow<KeepFailedSlipsUiState> = combine(
        ledger.queuedSlips,
        settings.choices,
    ) { queued, choices -> state(queued, choices.retention) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = state(emptyList(), settings.choices.value.retention),
        )

    fun choose(days: Int) {
        RetentionChoices.firstOrNull { it.inWholeDays.toInt() == days }?.let(settings::setRetention)
    }

    private fun state(queued: List<QueuedSlip>, retention: Duration) = KeepFailedSlipsUiState(
        options = RetentionChoices.map { choice ->
            RetentionOptionUiModel(
                days = choice.inWholeDays.toInt(),
                label = retentionPhrase(choice),
                chosen = choice == retention,
            )
        },
        kept = queued.size.let { held ->
            if (held == 0) AppText.copy.settings.keptNone else AppText.copy.settings.keptWaiting(held)
        },
        deadline = queued.deadline(retention),
    )

    /**
     * The soonest to expire is the one worth naming: it is the deadline the reader has, and
     * quoting the newest slip's would promise time the oldest does not have.
     */
    private fun List<QueuedSlip>.deadline(retention: Duration): String? {
        val soonest = minOfOrNull {
            evidenceDaysRemaining(it.capturedAtEpochMillis, clock.now().toEpochMilliseconds(), retention)
        } ?: return null

        return if (soonest == 0) {
            AppText.copy.settings.oldestLosesImageToday
        } else {
            AppText.copy.settings.oldestDaysLeft(soonest)
        }
    }
}
