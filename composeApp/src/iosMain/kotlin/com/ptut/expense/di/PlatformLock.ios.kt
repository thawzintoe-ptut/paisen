package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.platform.lock.DeviceAuth
import com.ptut.expense.platform.lock.LocalAuthDeviceAuth

actual fun deviceAuth(context: PtutDatabaseContext): DeviceAuth = LocalAuthDeviceAuth()
