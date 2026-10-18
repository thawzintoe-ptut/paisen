package com.ptut.expense.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Spec page 02, held to its own numbers. The values are copy in the same way the Settings rows
 * are copy — a screen reading the wrong one is not a crash, it is an app that moves differently
 * from the design nobody will re-measure.
 */
class PtutMotionTest {

    private val motion = PtutMotion.Default

    /**
     * Page 02 gives each kind of arrival its own duration. Sky's p02 and p10 put the sheet at 300,
     * which is also what Material's own sheet runs at (#131).
     */
    @Test
    fun `the timed values are the ones page 02 names`() {
        assertEquals(300.milliseconds, motion.push)
        assertEquals(300.milliseconds, motion.sheet)
        assertEquals(300.milliseconds, motion.dialog)
        assertEquals(200.milliseconds, motion.scrim)
    }

    /** Rev 3 page 11b keeps Undo apart from a save toast; collapsing the two is the mistake asserted. */
    @Test
    fun `the undo window and the toast dwell are not the same value`() {
        assertEquals(2_600.milliseconds, motion.toastHold)
        assertEquals(4.seconds, motion.undoWindow)
        assertNotEquals(motion.toastHold, motion.undoWindow)
    }

    @Test
    fun `every duration survives the trip to the whole milliseconds Compose counts in`() {
        listOf(motion.push, motion.sheet, motion.dialog, motion.toastRise)
            .forEach { assertEquals(it.inWholeMilliseconds, it.inMillis.toLong(), "$it lost precision") }
    }

    /**
     * `cubic-bezier(.2,.8,.2,1)` leaves fast and lands slow, so it is past halfway before half the
     * time has gone. Asserted by what the curve does rather than by its control points, which
     * `CubicBezierEasing` does not hand back.
     */
    @Test
    fun `the standard curve starts fast and settles`() {
        assertEquals(0f, motion.standard.transform(0f))
        assertEquals(1f, motion.standard.transform(1f))
        assertTrue(motion.standard.transform(0.5f) > 0.5f, "the standard curve is not front-loaded")
    }

    /** The overshoot goes past its target and comes back; that is the whole reason it exists. */
    @Test
    fun `the overshoot curve passes one before it ends`() {
        val peak = (1..99).maxOf { motion.overshoot.transform(it / 100f) }

        assertTrue(peak > 1f, "the overshoot curve never overshoots")
    }

    /**
     * Page 01: four curves, each doing a job the other three must not. Asserted together rather
     * than one test each, because what makes them a system is the contrast — emphasized has to be
     * ahead of standard, exit has to be behind both, and the spring has to be the only one that
     * passes its target. "Overshoot is reserved for objects the user summoned."
     */
    @Test
    fun `the four curves are four different jobs`() {
        val quarter = 0.25f
        assertTrue(
            motion.emphasized.transform(quarter) > motion.standard.transform(quarter),
            "emphasized is not more front-loaded than standard",
        )
        assertTrue(
            motion.exit.transform(0.5f) < 0.5f,
            "the exit curve settles instead of accelerating away",
        )
        listOf(motion.standard, motion.emphasized, motion.exit).forEach { curve ->
            assertTrue(
                (0..100).maxOf { curve.transform(it / 100f) } <= 1f,
                "a curve that is not the spring overshoots",
            )
        }
    }
}
