package com.ptut.expense.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechLanguageTest {

    @Test
    fun `an offline pack names its language by its full tag`() {
        assertEquals(SpeechLanguage.English, SpeechLanguage.forPack("en-US"))
        assertEquals(SpeechLanguage.English, SpeechLanguage.forPack("en_us"))
        assertEquals(SpeechLanguage.Thai, SpeechLanguage.forPack("th-TH"))
        assertEquals(SpeechLanguage.Burmese, SpeechLanguage.forPack("my-MM"))
    }

    /** The mic listens in en-US, so an en-GB pack on the phone cannot answer for it. */
    @Test
    fun `a pack for another region of a language does not stand in for ours`() {
        assertNull(SpeechLanguage.forPack("en-GB"))
        assertNull(SpeechLanguage.forPack("en-IN"))
    }
}
