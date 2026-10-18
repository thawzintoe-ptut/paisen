package com.ptut.expense.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** #144 and #155: which screens the pill shows over, and the slot it lights there. */
class PillSlotsTest {

    @Test
    fun everyDestinationLightsItsOwnSlot() {
        BarDestination.entries.forEach { destination ->
            assertEquals(destination, PillSlots[destination.route::class], destination.label)
        }
    }

    /** N4: a screen Settings opens lights Settings, so the reader can see where they are. */
    @Test
    fun categoriesAndActivityLightSettingsBecauseSettingsIsWhereTheyAreReachedFrom() {
        assertEquals(BarDestination.Settings, PillSlots[PtutRoute.Categories::class])
        assertEquals(BarDestination.Settings, PillSlots[PtutRoute.Activity::class])
    }

    @Test
    fun theTaskScreensHideThePill() {
        listOf(
            PtutRoute.Review::class,
            PtutRoute.ConfirmSlip::class,
            PtutRoute.TransactionDetail::class,
            PtutRoute.EditTransaction::class,
            PtutRoute.EditCategory::class,
            PtutRoute.PhotoFolders::class,
        ).forEach { route -> assertNull(PillSlots[route], route.simpleName) }
    }
}
