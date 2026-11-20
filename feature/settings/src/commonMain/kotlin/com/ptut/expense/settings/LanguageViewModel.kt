package com.ptut.expense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.settings.SaveLanguageUseCase
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.Language
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LanguageOptionUiModel(val language: Language, val label: String, val chosen: Boolean)

data class LanguageUiState(val options: List<LanguageOptionUiModel>)

/** The app's language. A choice is saved, then the shell is rebuilt in it and opens on Settings again. */
class LanguageViewModel(
    private val saveLanguage: SaveLanguageUseCase = SaveLanguageUseCase(),
    private val offered: List<Language> = Language.offered(offersUnreleased = false),
) : ViewModel() {

    val uiState: StateFlow<LanguageUiState> = AppText.language
        .map(::state)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS), state(AppText.language.value))

    fun choose(language: Language) {
        saveLanguage(language)
        AppText.choose(language)
    }

    private fun state(current: Language) = LanguageUiState(
        options = offered.map { LanguageOptionUiModel(language = it, label = it.label, chosen = it == current) },
    )
}
