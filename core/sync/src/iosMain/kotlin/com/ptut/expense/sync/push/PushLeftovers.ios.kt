package com.ptut.expense.sync.push

import platform.Foundation.NSUserDefaults

/**
 * Kept for the same account to delete on its return. No retiring here: Installations lives in the
 * Swift target, and an iPhone already drops a data push sent to another account.
 */
internal actual fun pushLeftovers(): PushLeftovers = UserDefaultsPushLeftovers

private object UserDefaultsPushLeftovers : PushLeftovers {
    private val defaults get() = NSUserDefaults.standardUserDefaults

    override fun read(): PushLeftover? {
        val token = defaults.stringForKey(KEY_TOKEN) ?: return null
        val uid = defaults.stringForKey(KEY_UID) ?: return null
        return PushLeftover(token, uid)
    }

    override fun write(leftover: PushLeftover?) {
        if (leftover == null) {
            defaults.removeObjectForKey(KEY_TOKEN)
            defaults.removeObjectForKey(KEY_UID)
        } else {
            defaults.setObject(leftover.token, forKey = KEY_TOKEN)
            defaults.setObject(leftover.uid, forKey = KEY_UID)
        }
    }

    override suspend fun retire(token: String): Boolean = false

    private const val KEY_TOKEN = "ptut-push-leftover-token"
    private const val KEY_UID = "ptut-push-leftover-uid"
}
