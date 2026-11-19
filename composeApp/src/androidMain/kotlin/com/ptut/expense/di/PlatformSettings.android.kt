package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.settings.AppSettingsStore
import com.ptut.expense.settings.SharedPreferencesAppSettingsStore

/** `applicationContext`, so a store the importer holds cannot outlive an Activity while holding one. */
actual fun appSettingsStore(context: PtutDatabaseContext): AppSettingsStore =
    SharedPreferencesAppSettingsStore(context.applicationContext)

actual fun phoneCountry(context: PtutDatabaseContext): String? {
    val telephony = context.applicationContext.getSystemService(android.content.Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
    return listOfNotNull(telephony?.networkCountryIso, telephony?.simCountryIso, java.util.Locale.getDefault().country)
        .firstOrNull { it.length == 2 }
}
