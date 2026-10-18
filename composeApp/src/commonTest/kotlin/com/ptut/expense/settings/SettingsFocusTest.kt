package com.ptut.expense.settings

import com.ptut.expense.domain.ImportFix
import com.ptut.expense.nav.PtutRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsFocusTest {

    @Test
    fun `the two fixes land on two different levers`() {
        assertEquals(SettingsAction.SlipSource, ImportFix.GrantPhotoAccess.owningRow())
        assertEquals(SettingsAction.BackgroundImport, ImportFix.ResumeWatching.owningRow())
    }

    @Test
    fun `every fix survives the trip through a route and back`() {
        ImportFix.entries.forEach { fix ->
            assertEquals(fix.owningRow(), fix.settingsRoute().focusedRow(), fix.name)
        }
    }

    @Test
    fun `Settings opened from the bar is focused on nothing`() {
        assertNull(PtutRoute.Settings().focusedRow())
    }

    @Test
    fun `a row name this build no longer has focuses nothing rather than failing`() {
        assertNull(PtutRoute.Settings(focusRow = "SomeRowRemovedLater").focusedRow())
    }
}
