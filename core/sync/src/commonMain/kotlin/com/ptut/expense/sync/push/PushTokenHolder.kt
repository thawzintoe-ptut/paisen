package com.ptut.expense.sync.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The address FCM knows this phone by, its Firebase installation ID, set by the platform
 * (Android's messaging service, the iOS push bridge) and read by [PushRegistrar]. Null until the
 * platform has one; a phone without Google Play services never sets it, and nothing registers.
 */
class PushTokenHolder {
    private val current = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = current.asStateFlow()

    fun set(token: String?) {
        current.value = token?.takeIf { it.isNotBlank() }
    }
}
