package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.settings.AppSettingsStore
import com.ptut.expense.settings.UserDefaultsAppSettingsStore
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.currentLocale

/** iOS carries no context: `NSUserDefaults` finds its own suite. */
actual fun appSettingsStore(context: PtutDatabaseContext): AppSettingsStore =
    UserDefaultsAppSettingsStore()

actual fun phoneCountry(context: PtutDatabaseContext): String? =
    NSLocale.currentLocale().objectForKey(NSLocaleCountryCode) as? String
