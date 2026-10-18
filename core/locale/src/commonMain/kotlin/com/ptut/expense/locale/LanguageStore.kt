package com.ptut.expense.locale

/** Where the language chosen in Settings survives: `SharedPreferences` on Android, `NSUserDefaults` on iOS. */
interface LanguageStore {
    /** Null until someone chooses, so the phone's own language decides the first launch. */
    fun read(): Language?

    fun write(language: Language)
}
