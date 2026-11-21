package com.ptut.expense.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.format
import com.ptut.expense.domain.formatCompactParts
import com.ptut.expense.domain.play.GuessResult
import com.ptut.expense.domain.play.GuessStart
import com.ptut.expense.domain.play.RevealGuessUseCase
import com.ptut.expense.domain.play.StartGuessUseCase
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** One side of p02's versus: who guessed, what, how far off, and whether it was closer. */
data class GuessRowUiModel(val who: String, val amount: String, val off: String, val closer: Boolean)

sealed interface GuessUiState {
    data object Loading : GuessUiState

    /** FR-86.9: fewer than two earlier weeks. The week opens as it does today. */
    data class Missing(val line: String, val seeWeek: String, val from: String, val to: String) : GuessUiState

    data class Guessing(
        val eyebrow: String,
        val title: String,
        val week: String,
        val symbol: String,
        val digits: String,
        val spoken: String,
        /** The slider's place and how many steps it has. */
        val index: Int,
        val steps: Int,
        val less: String,
        val more: String,
        val record: String,
        val guess: String,
        val skip: String,
        val sliderLabel: String,
        val local: String,
        val from: String,
        val to: String,
        /** "Your guess · ฿4,800" under the odometer, and PaiSeN's line with its disc (p01). */
        val yourGuess: String = "",
        val paisenToo: String = "",
        val disc: String = "",
    ) : GuessUiState

    data class Revealed(
        val eyebrow: String,
        val title: String,
        val week: String,
        val symbol: String,
        val digits: String,
        /** The roll starts from the reader's guess (FR-86.4). */
        val startFrom: String,
        val you: GuessRowUiModel,
        val paisen: GuessRowUiModel,
        val closerLabel: String,
        val outcome: String,
        val how: String,
        val howLines: List<String>,
        val seeWeek: String,
        val local: String,
        val from: String,
        val to: String,
        /** p02: the disc on PaiSeN's row, and the stamp shown when the reader was closer. */
        val disc: String = "",
        val youWon: Boolean = false,
        val stampFace: String = "",
        val stampCaption: String = "",
        val stampLabel: String = "",
    ) : GuessUiState
}

/**
 * #86, p01 and p02. PaiSeN's guess is stored before the slider shows (FR-86.3), the slider moves in
 * ฿100 steps over the reader's own range, and the reveal rolls from the reader's guess to the total.
 */
class GuessViewModel(
    monday: String,
    private val startGuess: StartGuessUseCase,
    private val revealGuess: RevealGuessUseCase,
) : ViewModel() {

    private val week: LocalDate? = runCatching { LocalDate.parse(monday) }.getOrNull()
    private val state = MutableStateFlow<GuessUiState>(GuessUiState.Loading)
    val uiState: StateFlow<GuessUiState> = state.asStateFlow()

    private var start: GuessStart? = null
    private var guess: Satang = Satang.Zero
    private var revealing = false

    init {
        viewModelScope.launch {
            val day = week ?: return@launch state.update { missing(null) }
            val started = runSuspendCatching { startGuess(day) }.getOrNull() ?: return@launch state.update { missing(day) }
            start = started
            val revealed = started.revealed
            if (revealed != null) {
                state.value = revealed(day, started, revealed)
            } else {
                guess = started.paisen.amount.coerceIn(started.range.min, started.range.max)
                state.value = guessing(day, started)
            }
        }
    }

    /** − and + move one ฿100 step each. */
    fun nudge(steps: Int) = moveTo(guess + Satang.ofMinorUnits(steps * (start?.range?.step?.toMinorUnits() ?: 0L)))

    /** The slider's place, in steps from its low end. */
    fun set(index: Int) {
        val range = start?.range ?: return
        moveTo(range.min + Satang.ofMinorUnits(index * range.step.toMinorUnits()))
    }

    fun reveal() {
        val day = week ?: return
        val started = start ?: return
        if (revealing || state.value !is GuessUiState.Guessing) return
        revealing = true
        viewModelScope.launch {
            // A write that fails leaves the slider as it was, so the reader can try again.
            runSuspendCatching { revealGuess(day, guess) }
                .onSuccess { result -> state.value = revealed(day, started, result) }
            revealing = false
        }
    }

    private fun moveTo(amount: Satang) {
        val day = week ?: return
        val started = start ?: return
        if (state.value !is GuessUiState.Guessing) return
        guess = amount.coerceIn(started.range.min, started.range.max)
        state.value = guessing(day, started)
    }

    private fun guessing(monday: LocalDate, started: GuessStart): GuessUiState.Guessing {
        val text = AppText.copy.play.guess
        val range = started.range
        val step = range.step.toMinorUnits()
        val parts = guess.formatCompactParts()
        return GuessUiState.Guessing(
            eyebrow = text.eyebrow,
            title = text.title,
            week = weekLabel(monday),
            symbol = parts.symbol,
            digits = parts.whole,
            spoken = guess.format(),
            index = ((guess.toMinorUnits() - range.min.toMinorUnits()) / step).toInt(),
            steps = ((range.max.toMinorUnits() - range.min.toMinorUnits()) / step).toInt(),
            less = text.less,
            more = text.more,
            record = started.record.bestPercent?.let { text.record(it, started.record.weeksPlayed) } ?: text.firstTime,
            guess = text.guess,
            skip = text.skip,
            sliderLabel = text.sliderLabel,
            yourGuess = "${text.sliderLabel} · ${guess.format()}",
            paisenToo = text.paisenToo,
            disc = AppText.copy.play.coach.disc,
            local = AppText.copy.play.local,
            from = monday.toString(),
            to = monday.plus(LAST_DAY, DateTimeUnit.DAY).toString(),
        )
    }

    private fun revealed(monday: LocalDate, started: GuessStart, result: GuessResult): GuessUiState.Revealed {
        val text = AppText.copy.play.guess
        val total = result.total.formatCompactParts()
        return GuessUiState.Revealed(
            eyebrow = text.eyebrow,
            title = text.revealTitle,
            week = weekLabel(monday),
            symbol = total.symbol,
            digits = total.whole,
            startFrom = result.you.formatCompactParts().whole,
            you = GuessRowUiModel(text.you, result.you.format(), offLine(result.total, result.you), closer = result.youCloser),
            paisen = GuessRowUiModel(text.paisen, result.paisen.format(), offLine(result.total, result.paisen), closer = !result.youCloser),
            closerLabel = text.closer,
            outcome = if (result.youCloser) text.youWon else text.paisenWon,
            how = text.how,
            howLines = started.paisen.weeks.mapIndexed { index, amount ->
                val weekStart = monday.plus(index - started.paisen.weeks.size, DateTimeUnit.WEEK)
                text.howWeek(weekStart.dayLabel(), amount.format())
            } + text.howTrend(started.paisen.trend.format()),
            seeWeek = text.seeWeek,
            local = AppText.copy.play.local,
            from = monday.toString(),
            to = monday.plus(LAST_DAY, DateTimeUnit.DAY).toString(),
            disc = AppText.copy.play.coach.disc,
            youWon = result.youCloser,
            stampFace = AppText.copy.play.stamps.faces["BeatPaiSeN"].orEmpty(),
            stampCaption = AppText.copy.play.stamps.captionWin,
            stampLabel = AppText.copy.play.stamps.names["BeatPaiSeN"].orEmpty(),
        )
    }

    /** A route that named no real Monday still opens, on nothing to guess. */
    private fun missing(monday: LocalDate?): GuessUiState.Missing {
        val text = AppText.copy.play.guess
        return GuessUiState.Missing(text.missing, text.seeWeek, monday?.toString().orEmpty(), monday?.plus(LAST_DAY, DateTimeUnit.DAY)?.toString().orEmpty())
    }

    private fun offLine(total: Satang, guessed: Satang): String {
        val text = AppText.copy.play.guess
        val off = if (total > guessed) total - guessed else guessed - total
        return if (off == Satang.Zero) text.spotOn else text.off(off.format())
    }

    private fun weekLabel(monday: LocalDate): String =
        AppText.copy.play.guess.week(monday.dayLabel(), monday.plus(LAST_DAY, DateTimeUnit.DAY).dayLabel())
}

/** Sunday is six days after Monday. */
private const val LAST_DAY = 6
