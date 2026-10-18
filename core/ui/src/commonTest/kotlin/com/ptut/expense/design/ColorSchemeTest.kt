package com.ptut.expense.design

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #148. A scheme is built from the palette it is handed, so a second look needs a second palette
 * and nothing else.
 */
class ColorSchemeTest {

    // A palette no theme ships, so a builder that ignored its argument cannot pass by accident.
    private val marked = Sky.copy(
        primary = Color(0xFF123456),
        surface = Color(0xFF654321),
        warn = Color(0xFF0A0B0C),
    )

    @Test
    fun aLightSchemeTakesItsColoursFromThePaletteItIsHanded() {
        val scheme = lightScheme(marked)

        assertEquals(marked.primary, scheme.primary)
        assertEquals(marked.surface, scheme.surface)
        assertEquals(marked.warn, scheme.error)
    }

    @Test
    fun aDarkSchemeTakesItsColoursFromThePaletteItIsHanded() {
        val scheme = darkScheme(marked)

        assertEquals(marked.primary, scheme.primary)
        assertEquals(marked.surface, scheme.surface)
        assertEquals(marked.warn, scheme.error)
    }
}
