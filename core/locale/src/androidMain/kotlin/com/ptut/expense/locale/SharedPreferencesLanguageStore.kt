package com.ptut.expense.locale

import android.content.Context

class SharedPreferencesLanguageStore(context: Context) : LanguageStore {
    private val choices = context.getSharedPreferences(CHOICES_FILE, Context.MODE_PRIVATE)

    override fun read(): Language? = choices.getString(KEY_LANGUAGE, null)?.let(Language::of)

    override fun write(language: Language) {
        choices.edit().putString(KEY_LANGUAGE, language.tag).apply()
    }

    private companion object {
        const val CHOICES_FILE = "app_language"
        const val KEY_LANGUAGE = "language"
    }
}
