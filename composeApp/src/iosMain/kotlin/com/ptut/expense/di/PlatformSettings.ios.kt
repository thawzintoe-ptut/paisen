package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.settings.ImportSettingsStore
import com.ptut.expense.settings.UserDefaultsImportSettingsStore
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.currentLocale

/** iOS carries no context: `NSUserDefaults` finds its own suite. */
actual fun importSettingsStore(context: PtutDatabaseContext): ImportSettingsStore =
    UserDefaultsImportSettingsStore()

actual fun phoneCountry(context: PtutDatabaseContext): String? =
    NSLocale.currentLocale().objectForKey(NSLocaleCountryCode) as? String
