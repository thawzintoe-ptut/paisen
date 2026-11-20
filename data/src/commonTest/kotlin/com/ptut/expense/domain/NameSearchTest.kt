package com.ptut.expense.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NameSearchTest {

    @Test
    fun anEmptyOrBlankQueryMatchesEveryName() {
        assertTrue("Flat 12".matchesQuery(""))
        assertTrue("Flat 12".matchesQuery("   "))
    }

    @Test
    fun aQueryMatchesAnywhereInEitherCase() {
        assertTrue("Yangon trip".matchesQuery("TRIP"))
        assertTrue("Yangon trip".matchesQuery(" gon "))
    }

    @Test
    fun aQueryNotInTheNameDoesNotMatch() {
        assertFalse("Flat 12".matchesQuery("trip"))
    }
}
