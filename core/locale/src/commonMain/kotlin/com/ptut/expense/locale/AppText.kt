package com.ptut.expense.locale

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The language every screen is written in, an object for the reason [com.ptut.expense.agent.AgentInbox]
 * is one: ViewModels, toasts and the domain's day names read it, and none of them lives in a
 * composition. The shell is rebuilt when it changes (`App`), so text a screen wrote once is
 * written again.
 */
object AppText {
    private val chosen = MutableStateFlow(Language.English)
    val language: StateFlow<Language> = chosen.asStateFlow()

    val copy: Copy get() = chosen.value.copy

    /** Set when Settings changes the language, so the rebuilt shell opens on Settings again. */
    var reopenSettings: Boolean = false
        private set

    /** At launch: the saved choice, or the phone's language. */
    fun use(language: Language) {
        chosen.value = language
    }

    /** From the Language screen. */
    fun choose(language: Language) {
        if (language == chosen.value) return
        reopenSettings = true
        chosen.value = language
    }

    fun settingsReopened() {
        reopenSettings = false
    }
}

/** [AppText.copy] for composables. `App` provides it, keyed on the language. */
val LocalCopy = staticCompositionLocalOf { EnglishCopy }
