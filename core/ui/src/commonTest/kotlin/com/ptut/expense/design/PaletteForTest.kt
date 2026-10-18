package com.ptut.expense.design

import kotlin.test.Test
import kotlin.test.assertSame

/** #143. The system dark setting picks one of two palettes, and nothing else does. */
class PaletteForTest {

    @Test
    fun skyWearsSkyByDayAndSkyNightByNight() {
        assertSame(Sky, paletteFor(darkTheme = false))
        assertSame(SkyNight, paletteFor(darkTheme = true))
    }
}
