package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.onboarding.OnboardingFlag
import com.ptut.expense.onboarding.UserDefaultsOnboardingFlag
import com.ptut.expense.platform.photos.PhotoAccessRequest
import com.ptut.expense.platform.photos.PhotoLibraryAccess

/** iOS carries no context: `NSUserDefaults` finds its own suite. */
actual fun onboardingFlag(context: PtutDatabaseContext): OnboardingFlag =
    UserDefaultsOnboardingFlag()

/**
 * The album gate is already the one place that asks, so it answers this seam rather than being
 * wrapped by a second class that would only forward to it. It holds no state of its own — every
 * call reads `PHPhotoLibrary` — so the copy the slip source gates on is the same answer as this one.
 */
actual fun photoAccessRequest(context: PtutDatabaseContext): PhotoAccessRequest =
    PhotoLibraryAccess()
