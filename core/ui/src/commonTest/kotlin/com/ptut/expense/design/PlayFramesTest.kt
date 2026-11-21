package com.ptut.expense.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayFramesTest {

    private val motion = PtutMotion.Default

    @Test
    fun `a stamp starts large and lands at its own size`() {
        assertEquals(STAMP_PRESS_FROM, stampScaleAt(0f, motion.press))
        assertEquals(1f, stampScaleAt(1f, motion.press))
    }

    @Test
    fun `the press dips under its size before it settles`() {
        val frames = (0..20).map { stampScaleAt(it / 20f, motion.press) }

        assertTrue(frames.any { it < 1f }, "the press curve overshoots, so the stamp lands past its size")
    }

    @Test
    fun `a card turns from face down to face up`() {
        assertEquals(0f, cardAngleAt(0f, motion.turn))
        assertEquals(180f, cardAngleAt(1f, motion.turn))
    }

    @Test
    fun `the back shows from the card's edge on`() {
        assertFalse(showsBack(89f))
        assertTrue(showsBack(90f))
        assertTrue(showsBack(185f))
    }

    @Test
    fun `a meter stays inside its track`() {
        assertEquals(0f, meterShare(-0.2f))
        assertEquals(1f, meterShare(1.4f))
        assertEquals(0f, meterShare(Float.NaN))
        assertEquals(0.5f, meterShare(0.5f))
    }
}
