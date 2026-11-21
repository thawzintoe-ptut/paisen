package com.ptut.expense.ui.sky

import kotlin.test.Test
import kotlin.test.assertEquals

class SkyRollingReelsTest {

    @Test
    fun `a first showing counts up from nothing at length`() {
        assertEquals(RollStart(from = "", long = true), rollStart(shown = "", startFrom = null))
    }

    @Test
    fun `a first showing with a start rolls from it at length`() {
        assertEquals(RollStart(from = "1,200", long = true), rollStart(shown = "", startFrom = "1,200"))
    }

    @Test
    fun `a figure kept from before starts there and ignores the start`() {
        assertEquals(RollStart(from = "900", long = false), rollStart(shown = "900", startFrom = "1,200"))
    }
}
