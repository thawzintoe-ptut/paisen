package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.settings.AppSettingsStore
import com.ptut.expense.settings.UserDefaultsAppSettingsStore

/** iOS carries no context: `NSUserDefaults` finds its own suite. */
actual fun appSettingsStore(context: PtutDatabaseContext): AppSettingsStore =
    UserDefaultsAppSettingsStore()
