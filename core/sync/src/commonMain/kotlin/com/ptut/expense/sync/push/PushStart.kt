package com.ptut.expense.sync.push

import com.ptut.expense.sync.auth.Account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Runs [start] once, the first time [account] is signed in. A phone that never signs in never
 * starts the push service, so nothing about it reaches Firebase.
 */
fun CoroutineScope.startWhenSignedIn(account: StateFlow<Account>, start: () -> Unit): Job = launch {
    account.first { it is Account.SignedIn }
    start()
}
