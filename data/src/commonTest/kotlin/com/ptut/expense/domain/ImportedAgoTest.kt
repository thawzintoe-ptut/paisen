package com.ptut.expense.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ImportedAgoTest {

    @Test
    fun countsInTheLargestUnitThatStillReadsAsANumber() {
        assertEquals("under a minute", elapsedPhrase(20 * 1000L))
        assertEquals("2 min", elapsedPhrase(2 * 60 * 1000L))
        assertEquals("1 hour", elapsedPhrase(90 * 60 * 1000L))
        assertEquals("3 days", elapsedPhrase(3 * 24 * 60 * 60 * 1000L))
    }
}
