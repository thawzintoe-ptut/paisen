package com.ptut.expense.sync.auth

import com.ptut.expense.data.local.PtutDatabaseContext

/** #187 on iOS: the refresh token in the Keychain, through the Swift bridge. Nothing is kept without one. */
actual fun tokenStore(context: PtutDatabaseContext): TokenStore = KeychainTokenStore()

private class KeychainTokenStore : TokenStore {
    override fun load(): String? = IosBridges.auth?.keychainLoad(KEY)

    override fun save(value: String) {
        IosBridges.auth?.keychainSave(KEY, value)
    }

    override fun clear() {
        IosBridges.auth?.keychainDelete(KEY)
    }

    private companion object {
        const val KEY = "paisen.account.session"
    }
}
