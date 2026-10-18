package com.ptut.expense.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * C-4's done-means, asserted where the copy actually ships. The shared suite proves the screen
 * honours a cadence; this proves the one iOS hands it is the honest one.
 */
class SlipImportCadenceTest {

    private val cadence = slipImportCadence()

    @Test
    fun `the background import row cannot be switched`() {
        assertFalse(cadence.importIsAdjustable, "iOS has no job a switch there could turn off")
    }

    @Test
    fun `the rows say the album is only read when the app is opened`() {
        assertEquals("On each app open", cadence.importRhythm)
        assertTrue(cadence.importExplanation.contains("every time you open the app"), cadence.importExplanation)
    }
}
