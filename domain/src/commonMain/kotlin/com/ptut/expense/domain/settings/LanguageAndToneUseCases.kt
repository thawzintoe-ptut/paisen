package com.ptut.expense.domain.settings

import com.ptut.expense.locale.Language
import com.ptut.expense.locale.LanguageStore
import com.ptut.expense.locale.NoteTone

/** The app's language, kept for the next launch. Nothing is kept in a graph that binds no store. */
class SaveLanguageUseCase(private val store: LanguageStore? = null) {
    operator fun invoke(language: Language) {
        store?.write(language)
    }
}

/** #22: the weekly note's tone flips between plain and playful. */
class SwitchNoteToneUseCase(private val settings: AppSettings) {
    operator fun invoke() {
        val next = if (settings.choices.value.noteTone == NoteTone.Plain) NoteTone.Playful else NoteTone.Plain
        settings.setNoteTone(next)
    }
}
