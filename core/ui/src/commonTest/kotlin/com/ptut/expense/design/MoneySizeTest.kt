package com.ptut.expense.design

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D1 in #20. The sizes are what the platform made of the 68 design size: Android 14+ grows large
 * text far less than its font scale says, and iOS grows it in a straight line.
 */
class MoneySizeTest {

    @Test
    fun aMoneyRoleNeverDrawsBelowItsDesignSize() {
        assertEquals(68f, moneySize(designSize = 68f, platformSize = 57.8f))
    }

    @Test
    fun aMoneyRoleFollowsThePlatformInsideTheCap() {
        assertEquals(71.5f, moneySize(designSize = 68f, platformSize = 71.5f))
    }

    @Test
    fun aMoneyRoleStopsAtOnePointThreeTimesItsDesignSize() {
        assertEquals(88.4f, moneySize(designSize = 68f, platformSize = 136f), absoluteTolerance = 0.001f)
    }
}
