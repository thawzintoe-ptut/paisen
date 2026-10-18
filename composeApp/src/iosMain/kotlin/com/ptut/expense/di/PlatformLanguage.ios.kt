package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.locale.LanguageStore
import com.ptut.expense.locale.UserDefaultsLanguageStore
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/** iOS carries no context: `NSUserDefaults` finds its own suite. */
actual fun languageStore(context: PtutDatabaseContext): LanguageStore = UserDefaultsLanguageStore()

actual fun phoneLanguageTag(): String = NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"
