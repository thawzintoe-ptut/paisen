package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.settings.AppSettingsStore
import com.ptut.expense.settings.SharedPreferencesAppSettingsStore

/** `applicationContext`, so a store the importer holds cannot outlive an Activity while holding one. */
actual fun appSettingsStore(context: PtutDatabaseContext): AppSettingsStore =
    SharedPreferencesAppSettingsStore(context.applicationContext)
