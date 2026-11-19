package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.settings.AppSettingsStore
import com.ptut.expense.domain.shared.Currency
import com.ptut.expense.domain.shared.StartingCurrency
import kotlinx.datetime.TimeZone

/**
 * Where the reader's import choices survive, which is the same split first run's flag is:
 * `SharedPreferences` against `NSUserDefaults`, and neither can be written once for both.
 *
 * Takes the platform handle the graph already carries, exactly as [onboardingFlag] does.
 */
expect fun appSettingsStore(context: PtutDatabaseContext): AppSettingsStore

/**
 * The country the phone reports, two letters, or null. Android asks the mobile network the phone is
 * on (a Myanmar SIM roaming in Bangkok reads TH); iOS has no carrier country since iOS 16, so it
 * reads the phone's Region. Neither needs a permission.
 */
expect fun phoneCountry(context: PtutDatabaseContext): String?

/** The phone's country as a currency, asked each time: a SIM or a trip can change it. */
class PhoneCurrency(private val country: () -> String?) {
    fun current(): Currency = StartingCurrency.forCountry(country())
}

/** #41: the zone a new home starts its days in, the phone's own where the person is now. */
fun phoneZone(): String = TimeZone.currentSystemDefault().id
