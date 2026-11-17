package com.ptut.expense.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PullMarkTest {

    @Test
    fun aGroupStillInTheMoneyThisPhoneHeldPullsFromItsMark() {
        assertEquals(1_000L, PullMark.kept(localCurrency = "MMK", localMark = 1_000L, serverCurrency = "MMK"))
    }

    @Test
    fun aGroupWhoseMoneyChangesOnPullIsPulledWhole() {
        assertNull(PullMark.kept(localCurrency = "THB", localMark = 1_000L, serverCurrency = "MMK"))
    }

    @Test
    fun aGroupNeverPulledHereIsPulledWhole() {
        assertNull(PullMark.kept(localCurrency = null, localMark = null, serverCurrency = "THB"))
    }
}
