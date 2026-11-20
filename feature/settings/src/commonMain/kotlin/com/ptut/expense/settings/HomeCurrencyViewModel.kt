package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.home.ChangeHomeCurrencyUseCase
import com.ptut.expense.domain.home.ConvertHomeCurrencyUseCase
import com.ptut.expense.domain.home.HomeCurrencyChange
import com.ptut.expense.domain.home.IsLedgerEmptyUseCase
import com.ptut.expense.domain.home.PreviewConversionUseCase
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.domain.shared.Rate
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

/** One currency in the list; [code] is what choosing the row passes back. */
data class CurrencyOptionUiModel(val code: String?, val label: String, val chosen: Boolean)

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
class HomeCurrencyViewModel(
    isLedgerEmpty: IsLedgerEmptyUseCase,
    private val changeHomeCurrency: ChangeHomeCurrencyUseCase,
    private val convertHomeCurrency: ConvertHomeCurrencyUseCase,
    private val previewConversion: PreviewConversionUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(stateFor(Home.money.code))
    val uiState: StateFlow<HomeCurrencyUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            isLedgerEmpty()
                .onSuccess { empty -> if (!empty) state.update { it.copy(note = currencyCopy().homeHasRows) } }
                .onFailure { saysItFailed() }
        }
    }

    fun choose(code: String?) {
        if (code == null || code == Home.money.code) return
        viewModelScope.launch {
            changeHomeCurrency(code) { choose -> Home.fromSettings { choose() } }
                .onSuccess { change ->
                    when (change) {
                        HomeCurrencyChange.Applied -> Unit

                        // A slip filed between the two reads makes the book refuse, and the screen says why.
                        HomeCurrencyChange.Refused -> state.update { it.copy(note = currencyCopy().homeHasRows) }

                        HomeCurrencyChange.NeedsRate -> {
                            val new = Currencies.of(code)
                            state.update {
                                it.copy(converting = ConvertUiModel(code, currencyCopy().convertTitle(code), unit = old().rateUnitLabel(), newSymbol = new.symbol))
                            }
                        }
                    }
                }.onFailure { saysItFailed() }
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
            if (!Home.fromSettings { convertHomeCurrency(converting.code, rate) }) {
                state.update {
                    if (it.converting?.code == converting.code) it.copy(converting = converting.copy(working = false, problem = currencyCopy().convertFailed)) else it
                }
            }
        }
    }

    private fun saysItFailed() = state.update { it.copy(note = AppText.copy.settings.didNotWork) }

    private fun old(): Currency = Currencies.of(Home.money.code)

    /** "฿100.00 becomes K12,500", from 100 of the old money's rate unit, or null while the rate is not a number. */
    private fun exampleAt(rateText: String, code: String): String? {
        val rate = Rate.parse(rateText, old().rateUnit) ?: return null
        val preview = previewConversion(code, rate)
        return currencyCopy().convertExample(formatMinor(preview.from, preview.fromCurrency), formatMinor(preview.to, preview.toCurrency))
    }

    private fun stateFor(chosen: String) = HomeCurrencyUiState(
        options = Currencies.all.map { CurrencyOptionUiModel(it.code, it.pickerLabel, chosen = it.code == chosen) },
        intro = currencyCopy().homeIntro(AppText.copy.settings.zonePlace(Home.choice.value.zoneId)),
    )
}
