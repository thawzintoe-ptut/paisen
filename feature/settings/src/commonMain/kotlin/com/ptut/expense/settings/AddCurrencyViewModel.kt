package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.settings.ImportChoices
import com.ptut.expense.domain.settings.ImportSettings
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.locale.currencyCopy
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One row: null [code] follows the phone's country. */
data class CurrencyOptionUiModel(val code: String?, val label: String, val chosen: Boolean)

data class AddCurrencyUiState(val options: List<CurrencyOptionUiModel>)

/** Which currency the Add sheet opens on. The first row follows the phone; the rest pin one. */
class AddCurrencyViewModel(
    private val settings: ImportSettings,
    /** What the phone's country maps to, read when the screen opens. */
    private val phoneCurrency: () -> Currency = { Currencies.Thb },
) : ViewModel() {

    val uiState: StateFlow<AddCurrencyUiState> = settings.choices
        .map(::state)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state(settings.choices.value))

    fun choose(code: String?) = settings.setAddCurrency(code)

    private fun state(choices: ImportChoices): AddCurrencyUiState {
        val follow = CurrencyOptionUiModel(null, currencyCopy().followPhone(phoneCurrency().code), chosen = choices.addCurrency == null)
        val pinned = Currencies.all.map { CurrencyOptionUiModel(it.code, "${it.code}  ${it.symbol}", chosen = it.code == choices.addCurrency) }
        return AddCurrencyUiState(listOf(follow) + pinned)
    }
}
