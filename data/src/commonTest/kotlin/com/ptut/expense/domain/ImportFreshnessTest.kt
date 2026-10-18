package com.ptut.expense.domain

import kotlin.test.Test
import kotlin.test.assertEquals

private const val HOUR = 60L * 60 * 1000
private const val DAY = 24 * HOUR

class ImportFreshnessTest {

    @Test
    fun staysSilentWithinADay() {
        assertEquals(ImportFreshness.Fresh, importFreshness(sinceLastImportMillis = 23 * HOUR))
    }

    @Test
    fun warnsBetweenOneAndThreeDays() {
        assertEquals(ImportFreshness.Stale, importFreshness(sinceLastImportMillis = 2 * DAY))
    }

    @Test
    fun escalatesPastThreeDays() {
        assertEquals(ImportFreshness.Broken, importFreshness(sinceLastImportMillis = 4 * DAY))
    }
}
