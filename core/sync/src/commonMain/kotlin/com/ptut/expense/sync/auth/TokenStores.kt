package com.ptut.expense.sync.auth

import com.ptut.expense.data.local.PtutDatabaseContext

/** The platform's secure store for the refresh token (#187). */
expect fun tokenStore(context: PtutDatabaseContext): TokenStore
