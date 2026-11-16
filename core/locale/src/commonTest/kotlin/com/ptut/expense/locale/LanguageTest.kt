package com.ptut.expense.locale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageTest {

    @Test
    fun `a Thai tag names Thai with or without its region`() {
        assertEquals(Language.Thai, Language.of("th"))
        assertEquals(Language.Thai, Language.of("th-TH"))
        assertEquals(Language.Thai, Language.of("th_TH"))
    }

    @Test
    fun `a tag the app is not written in names nothing`() {
        assertNull(Language.of("xx"))
        assertNull(Language.of(null))
    }

    @Test
    fun `a release build never opens in Thai on a Thai phone`() {
        assertEquals(Language.English, Language.atLaunch(saved = null, phoneTag = "th-TH", debug = false))
    }

    @Test
    fun `a debug build opens in Thai on a Thai phone`() {
        assertEquals(Language.Thai, Language.atLaunch(saved = null, phoneTag = "th-TH", debug = true))
    }

    /** F1.4: a tester picked Thai, then installed a build that hides it again. */
    @Test
    fun `a saved Thai that this build hides falls back to the phone's language`() {
        assertEquals(Language.Burmese, Language.atLaunch(saved = Language.Thai, phoneTag = "my-MM", debug = false))
    }

    @Test
    fun `a saved choice wins over the phone's language`() {
        assertEquals(Language.Burmese, Language.atLaunch(saved = Language.Burmese, phoneTag = "th-TH", debug = true))
    }
}
