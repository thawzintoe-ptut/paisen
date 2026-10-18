package com.ptut.expense.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val HOUR = 60L * 60 * 1000
private const val DAY = 24 * HOUR

class ImportFixTest {

    @Test
    fun asksForAccessWhenNothingHasEverRun() {
        assertEquals(ImportFix.GrantPhotoAccess, importFix(sinceLastImportMillis = null))
    }

    @Test
    fun offersNothingWhileTheImporterIsFresh() {
        assertNull(importFix(sinceLastImportMillis = 23 * HOUR))
    }

    @Test
    fun offersNothingInTheQuietStretchUnderThreeDays() {
        assertNull(importFix(sinceLastImportMillis = 2 * DAY))
    }

    @Test
    fun asksToResumeWatchingOnceTheRunsHaveStopped() {
        assertEquals(ImportFix.ResumeWatching, importFix(sinceLastImportMillis = 4 * DAY))
    }

    @Test
    fun tellsTheTwoBrokenTrustStatesApart() {
        assertEquals(
            listOf(ImportFix.GrantPhotoAccess, ImportFix.ResumeWatching),
            listOf(importFix(null), importFix(4 * DAY)),
        )
    }
}
