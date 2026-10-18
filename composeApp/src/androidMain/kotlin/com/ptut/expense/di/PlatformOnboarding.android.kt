package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.onboarding.OnboardingFlag
import com.ptut.expense.onboarding.SharedPreferencesOnboardingFlag
import com.ptut.expense.platform.photos.LauncherPhotoAccessRequest
import com.ptut.expense.platform.photos.PhotoAccessRequest

/** `applicationContext`, so the flag cannot outlive an Activity while holding one. */
actual fun onboardingFlag(context: PtutDatabaseContext): OnboardingFlag =
    SharedPreferencesOnboardingFlag(context.applicationContext)

/** No context: the launcher belongs to the Activity, which registers itself against this. */
actual fun photoAccessRequest(context: PtutDatabaseContext): PhotoAccessRequest =
    LauncherPhotoAccessRequest()
