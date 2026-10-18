package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.locale.LanguageStore
import com.ptut.expense.locale.SharedPreferencesLanguageStore
import java.util.Locale

actual fun languageStore(context: PtutDatabaseContext): LanguageStore =
    SharedPreferencesLanguageStore(context.applicationContext)

actual fun phoneLanguageTag(): String = Locale.getDefault().toLanguageTag()
