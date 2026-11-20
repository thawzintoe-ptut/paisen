package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.settings.AppSettingsStore
import kotlinx.datetime.TimeZone

/**
 * Where the reader's import choices survive, which is the same split first run's flag is:
 * `SharedPreferences` against `NSUserDefaults`, and neither can be written once for both.
 *
 * Takes the platform handle the graph already carries, exactly as [onboardingFlag] does.
 */
expect fun appSettingsStore(context: PtutDatabaseContext): AppSettingsStore

/** #41: the zone a new home starts its days in, the phone's own where the person is now. */
fun phoneZone(): String = TimeZone.currentSystemDefault().id
