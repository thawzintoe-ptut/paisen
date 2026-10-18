package com.ptut.expense.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Page 05's three marks, held to the JSON they were drawn from.
 *
 * The geometry is pure so it can be checked here rather than by eye: each of the three is a
 * function from "how far through the loop" to what to draw, and the three `anim` JSON files give the frame
 * numbers to check it against. What a Canvas then does with those numbers is the easy half.
 */
class MarkFramesTest {

    /**
     * `pulse-dot.json`: the halo scales 40% → 190% and fades 55% → 0 by frame 78 of 96, and the
     * last 18 frames are the still dot alone. That rest is what stops a 1.6s loop reading as a
     * stutter, so it is asserted rather than left to the easing.
     */
    @Test
    fun `the pulse halo swells out of the dot and is gone before the loop restarts`() {
        val start = pulseFrameAt(0f)
        assertEquals(0.40f, start.haloScale, "the halo does not start at the dot")
        assertEquals(0.55f, start.haloAlpha)

        val dead = pulseFrameAt(78f / 96f)
        assertEquals(1.90f, dead.haloScale, "the halo does not reach its full spread")
        assertEquals(0f, dead.haloAlpha, "the halo is still visible when the loop restarts")

        assertEquals(dead, pulseFrameAt(1f), "the halo comes back before the loop does")
        assertTrue(pulseFrameAt(0.2f).haloAlpha > pulseFrameAt(0.5f).haloAlpha, "the halo brightens")
    }

    /**
     * `check-draw.json`: 36 frames. The stroke draws on between frames 2 and 24 while the whole
     * mark scales 86% to 104% at frame 14 and settles at 100% by frame 26. Both have to be
     * finished by the end, because this one plays once and holds — "a check that loops turns a
     * completed action into an unfinished one".
     */
    @Test
    fun `the check strokes itself on and overshoots once and then holds`() {
        val start = checkFrameAt(0f)
        assertEquals(0.86f, start.scale, "the check does not start under size")
        assertEquals(0f, start.drawn, "the check is already part-drawn on the first frame")

        assertEquals(1.04f, checkFrameAt(14f / 36f).scale, "the check never overshoots")
        assertEquals(1f, checkFrameAt(26f / 36f).scale, "the check does not settle at full size")
        assertEquals(1f, checkFrameAt(24f / 36f).drawn, "the stroke is unfinished when it should hold")
        // Frame 26, not 24: the stroke is on by 24 but the mark is still settling out of its
        // overshoot, so 26 is the first frame there is nothing left to play.
        assertEquals(checkFrameAt(26f / 36f), checkFrameAt(1f), "the check does not hold once it is done")
    }

    /**
     * `arc-loop.json`: 180 frames. The trim end breathes 18% to 68% and back while rotation and
     * trim offset together turn the ring 540 degrees.
     *
     * The gap closing again is what stops the ring reading as a progress bar that reset, so the
     * sweep is asserted as a round trip rather than as a ramp.
     */
    @Test
    fun `the arc's gap opens half-way round and closes again as it turns`() {
        val start = arcFrameAt(0f)
        assertEquals(0.18f * 360f, start.sweepDegrees, "the arc does not start at its tightest")
        assertEquals(0f, start.turnDegrees)

        assertEquals(0.68f * 360f, arcFrameAt(0.5f).sweepDegrees, "the gap never opens fully")
        assertEquals(start.sweepDegrees, arcFrameAt(1f).sweepDegrees, "the gap does not close again")
        assertEquals(540f, arcFrameAt(1f).turnDegrees, "the ring does not turn a full cycle")
        assertTrue(arcFrameAt(0.25f).turnDegrees < arcFrameAt(0.75f).turnDegrees, "the ring stalls")
    }
}
