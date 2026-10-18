package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.settings.ImportSettingsStore
import com.ptut.expense.settings.SharedPreferencesImportSettingsStore

/** `applicationContext`, so a store the importer holds cannot outlive an Activity while holding one. */
actual fun importSettingsStore(context: PtutDatabaseContext): ImportSettingsStore =
    SharedPreferencesImportSettingsStore(context.applicationContext)
