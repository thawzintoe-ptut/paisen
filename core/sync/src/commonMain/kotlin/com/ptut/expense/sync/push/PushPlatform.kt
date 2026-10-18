package com.ptut.expense.sync.push

import com.ptut.expense.sync.FirebaseConfig

/** "android" or "ios": which push payload the Worker builds for this phone's `devices` document. */
expect val pushPlatform: String

/** A Worker is set in `firebase.properties`, so a shared or joined group is worth a notification ask. */
val pushConfigured: Boolean get() = FirebaseConfig.pushEnabled
