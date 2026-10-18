package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.locale.LanguageStore

/** Where the chosen language survives, the same split [importSettingsStore] is. */
expect fun languageStore(context: PtutDatabaseContext): LanguageStore

/** The phone's own language, a tag such as `my-MM`. It decides the first launch only. */
expect fun phoneLanguageTag(): String
