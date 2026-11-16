package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeChoice
import com.ptut.expense.domain.home.HomeBook
import com.ptut.expense.domain.minorPerWhole
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.convert
import com.ptut.expense.domain.shared.formatMinor
import com.ptut.expense.domain.shared.pickerLabel
import com.ptut.expense.domain.shared.rateUnitLabel
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.currencyCopy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeCurrencyUiState(
    val options: List<CurrencyOptionUiModel>,
    val intro: String,
    val note: String? = null,
    /** #47: the change waiting on a rate, for a ledger with rows. */
    val converting: ConvertUiModel? = null,
)

/** #47: the new money per one of the old, typed, and what ฿100 (or 100 of the old) becomes at it. */
data class ConvertUiModel(
    val code: String,
    val title: String,
    val rateText: String = "",
    /** "฿": what one rate is typed for, in the old money. */
    val unit: String,
    val newSymbol: String,
    val example: String? = null,
    val problem: String? = null,
    val working: Boolean = false,
)

/**
 * #41: the ledger's home currency. On an empty ledger a choice applies at once, the shell is rebuilt
 * in it and opens on Settings again. With rows, every amount converts at a typed rate first (#47).
 */
class HomeCurrencyViewModel(private val book: HomeBook) : ViewModel() {

    private val state = MutableStateFlow(stateFor(Home.money.code))
    val uiState: StateFlow<HomeCurrencyUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            if (!book.isEmpty()) state.update { it.copy(note = currencyCopy().homeHasRows) }
        }
    }

    fun choose(code: String?) {
        if (code == null || code == Home.money.code) return
        viewModelScope.launch {
            if (book.isEmpty()) {
                // A slip filed between the two calls makes the book refuse, and the screen says why.
                if (!Home.fromSettings { book.choose(choiceOf(code)) }) state.update { it.copy(note = currencyCopy().homeHasRows) }
            } else {
                val new = Currencies.of(code)
                state.update {
                    it.copy(converting = ConvertUiModel(code, currencyCopy().convertTitle(code), unit = old().rateUnitLabel(), newSymbol = new.symbol))
                }
            }
        }
    }

    fun typeRate(text: String) = state.update { current ->
        val converting = current.converting ?: return@update current
        current.copy(converting = converting.copy(rateText = text, example = exampleAt(text, converting.code), problem = null))
    }

    fun cancelConvert() = state.update { it.copy(converting = null) }

    /**
     * One transaction in the book; the rebuilt shell opens on Settings in the new money. The zone
     * stays the ledger's own: a currency change does not move its days.
     */
    fun confirmConvert() {
        val converting = state.value.converting ?: return
        if (converting.working) return
        val rate = Rate.parse(converting.rateText, old().rateUnit) ?: return state.update {
            it.copy(converting = converting.copy(problem = currencyCopy().askRate))
        }
        state.update { it.copy(converting = converting.copy(working = true)) }
        viewModelScope.launch {
            if (!Home.fromSettings { book.convert(choiceOf(converting.code), rate) }) {
                state.update {
                    if (it.converting?.code == converting.code) it.copy(converting = converting.copy(working = false, problem = currencyCopy().convertFailed)) else it
                }
            }
        }
    }

    private fun old(): Currency = Currencies.of(Home.money.code)

    private fun choiceOf(code: String) = HomeChoice(Currencies.of(code).toHomeMoney(), Home.choice.value.zoneId)

    /** "฿100.00 becomes K12,500", from 100 of the old money's rate unit, or null while the rate is not a number. */
    private fun exampleAt(rateText: String, code: String): String? {
        val old = old()
        val rate = Rate.parse(rateText, old.rateUnit) ?: return null
        val sample = EXAMPLE_UNITS * old.rateUnit * minorPerWhole(Home.money)
        val new = Currencies.of(code)
        return currencyCopy().convertExample(formatMinor(sample, old), formatMinor(convert(sample, old, rate, new).toMinorUnits(), new))
    }

    private companion object {
        const val EXAMPLE_UNITS = 100L
    }

    private fun stateFor(chosen: String) = HomeCurrencyUiState(
        options = Currencies.all.map { CurrencyOptionUiModel(it.code, it.pickerLabel, chosen = it.code == chosen) },
        intro = currencyCopy().homeIntro(AppText.copy.settings.zonePlace(Home.choice.value.zoneId)),
    )
}
