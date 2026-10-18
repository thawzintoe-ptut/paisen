package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.domain.settings.ImportSettingsStore

/**
 * Where the reader's import choices survive, which is the same split first run's flag is:
 * `SharedPreferences` against `NSUserDefaults`, and neither can be written once for both.
 *
 * Takes the platform handle the graph already carries, exactly as [onboardingFlag] does.
 */
expect fun importSettingsStore(context: PtutDatabaseContext): ImportSettingsStore
