package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.platform.lock.BiometricDeviceAuth
import com.ptut.expense.platform.lock.DeviceAuth

actual fun deviceAuth(context: PtutDatabaseContext): DeviceAuth = BiometricDeviceAuth(context.applicationContext)
