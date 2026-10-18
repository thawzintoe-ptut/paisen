package com.ptut.expense.sync.push

import com.ptut.expense.sync.auth.Account
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PushStartTest {

    private val signedIn = Account.SignedIn(uid = "u1", name = "Aye", email = null, provider = "google.com")

    @Test
    fun aPhoneThatNeverSignsInNeverStartsPush() = runTest {
        val account = MutableStateFlow<Account>(Account.SignedOut)
        var starts = 0

        backgroundScope.startWhenSignedIn(account) { starts++ }
        runCurrent()

        assertEquals(0, starts)
    }

    @Test
    fun signingInStartsPushOnce() = runTest {
        val account = MutableStateFlow<Account>(Account.SignedOut)
        var starts = 0

        backgroundScope.startWhenSignedIn(account) { starts++ }
        runCurrent()
        account.value = signedIn
        runCurrent()

        assertEquals(1, starts)
    }

    @Test
    fun aPhoneAlreadySignedInAtLaunchStartsPush() = runTest {
        val account = MutableStateFlow<Account>(signedIn)
        var starts = 0

        backgroundScope.startWhenSignedIn(account) { starts++ }
        runCurrent()

        assertEquals(1, starts)
    }

    @Test
    fun signingOutAndInAgainDoesNotStartPushTwice() = runTest {
        val account = MutableStateFlow<Account>(signedIn)
        var starts = 0

        backgroundScope.startWhenSignedIn(account) { starts++ }
        runCurrent()
        account.value = Account.SignedOut
        runCurrent()
        account.value = signedIn.copy(uid = "u2")
        runCurrent()

        assertEquals(1, starts)
    }
}
