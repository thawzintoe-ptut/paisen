package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.settings.ImportSettingsStore
import com.ptut.expense.settings.UserDefaultsImportSettingsStore

/** iOS carries no context: `NSUserDefaults` finds its own suite. */
actual fun importSettingsStore(context: PtutDatabaseContext): ImportSettingsStore =
    UserDefaultsImportSettingsStore()
