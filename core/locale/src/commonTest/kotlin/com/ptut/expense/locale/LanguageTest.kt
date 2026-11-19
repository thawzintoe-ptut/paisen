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

    /** Decision 4: an install with no saved choice follows a Thai phone, after an update too. */
    @Test
    fun `a phone set to Thai opens in Thai when nothing is saved`() {
        assertEquals(Language.Thai, Language.atLaunch(saved = null, phoneTag = "th-TH", offersUnreleased = true))
    }

    @Test
    fun `a saved Thai opens in English once the Thai switch is off`() {
        assertEquals(Language.English, Language.atLaunch(saved = Language.Thai, phoneTag = "th-TH", offersUnreleased = false))
    }

    @Test
    fun `a saved choice wins over the phone's language`() {
        assertEquals(Language.Burmese, Language.atLaunch(saved = Language.Burmese, phoneTag = "th-TH", offersUnreleased = false))
    }
}
