package com.ptut.expense

import com.ptut.expense.platform.lock.DeviceAuth

/** #25: a phone with no screen lock, so App lock never draws in a screen test. */
internal object NoDeviceAuth : DeviceAuth {
    override fun isAvailable(): Boolean = false

    override suspend fun authenticate(reason: String): Boolean = false
}
