package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.platform.lock.DeviceAuth

/** #25: the phone's own fingerprint, face or PIN check. BiometricPrompt against LAContext. */
expect fun deviceAuth(context: PtutDatabaseContext): DeviceAuth
