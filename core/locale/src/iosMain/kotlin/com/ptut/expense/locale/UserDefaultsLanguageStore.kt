package com.ptut.expense.locale

import platform.Foundation.NSUserDefaults

class UserDefaultsLanguageStore : LanguageStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(): Language? = defaults.stringForKey(KEY_LANGUAGE)?.let(Language::of)

    override fun write(language: Language) {
        defaults.setObject(language.tag, forKey = KEY_LANGUAGE)
    }

    private companion object {
        const val KEY_LANGUAGE = "ptut-app-language"
    }
}
