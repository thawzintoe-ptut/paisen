package com.ptut.expense.sync.push

import com.ptut.expense.sync.FirebaseConfig

actual val pushPlatform: String = "ios"

/** Whether the Firebase project has the Apple push key, for the app module's refresh task. */
val iosPushReady: Boolean get() = FirebaseConfig.IOS_PUSH_READY
