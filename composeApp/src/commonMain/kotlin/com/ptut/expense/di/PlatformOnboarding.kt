package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.onboarding.OnboardingFlag
import com.ptut.expense.platform.photos.PhotoAccessRequest

/**
 * What first run needs from the platform: somewhere the answer survives, and a way to raise the
 * system photo dialog. Neither can be written once for both — one is `SharedPreferences` against
 * `NSUserDefaults`, the other is an `ActivityResultLauncher` against `PHPhotoLibrary`.
 *
 * Both take the platform handle the graph already carries, exactly as [ledgerFileStore] does: on
 * Android that type is the `Context` they need, and on iOS it is an object with nothing on it.
 */
expect fun onboardingFlag(context: PtutDatabaseContext): OnboardingFlag

expect fun photoAccessRequest(context: PtutDatabaseContext): PhotoAccessRequest
