package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.Language
import com.ptut.expense.locale.LanguageStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LanguageOptionUiModel(val language: Language, val label: String, val chosen: Boolean)

data class LanguageUiState(val options: List<LanguageOptionUiModel>)

/** The app's language. A choice is saved, then the shell is rebuilt in it and opens on Settings again. */
class LanguageViewModel(private val store: LanguageStore? = null) : ViewModel() {

    val uiState: StateFlow<LanguageUiState> = AppText.language
        .map(::state)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state(AppText.language.value))

    fun choose(language: Language) {
        store?.write(language)
        AppText.choose(language)
    }

    private fun state(current: Language) = LanguageUiState(
        options = Language.entries.map { LanguageOptionUiModel(language = it, label = it.label, chosen = it == current) },
    )
}
