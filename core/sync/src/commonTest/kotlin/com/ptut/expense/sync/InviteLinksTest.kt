package com.ptut.expense.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InviteLinksTest {

    @Test
    fun aLinkGivesBackTheCodeAtTheEndOfItsPath() {
        assertEquals("ABC234XYZ", InviteLinks.codeIn("https://paisen.web.app/join/ABC234XYZ"))
    }

    @Test
    fun aQueryOrATrailingSlashAfterTheCodeIsIgnored() {
        assertEquals("ABC234XYZ", InviteLinks.codeIn("https://paisen.web.app/join/ABC234XYZ/?from=line"))
    }

    /** People read a code out loud and type it with spaces, dashes or in lower case. */
    @Test
    fun aTypedCodeIsReadWithoutSpacesOrDashesInAnyCase() {
        assertEquals("ABC234XYZ", InviteLinks.codeIn(" abc-234 xyz "))
    }

    @Test
    fun tooShortATextHoldsNoCode() {
        assertNull(InviteLinks.codeIn("ABC234"))
        assertNull(InviteLinks.codeIn("https://paisen.web.app/"))
    }

    /** 0 and O, 1 and I and L read alike, so a code never holds them and a text with them is not one. */
    @Test
    fun aNewCodeHasNineCharactersNoneOfThemEasyToMisread() {
        repeat(200) {
            val code = InviteLinks.newCode()
            assertEquals(InviteLinks.CODE_LENGTH, code.length)
            assertTrue(code.none { it in "01ILO" }, code)
            assertEquals(code, InviteLinks.codeIn(code))
        }
    }

    @Test
    fun anOfferedLinkWaitsUntilTheShellTakesIt() {
        InviteLinks.offer("https://paisen.web.app/join/ABC234XYZ")
        assertEquals("ABC234XYZ", InviteLinks.pendingCode.value)
        InviteLinks.consume()
        assertNull(InviteLinks.pendingCode.value)
    }

    @Test
    fun anOfferWithNoCodeLeavesNothingWaiting() {
        InviteLinks.consume()
        InviteLinks.offer("https://example.com/somewhere")
        assertNull(InviteLinks.pendingCode.value)
    }
}
