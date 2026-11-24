package com.ptut.expense.settings

import com.ptut.expense.di.AppBuild
import com.ptut.expense.locale.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedbackMailTest {

    @Test
    fun `the mail goes to the address the row shows and carries the version and the phone`() {
        val mail = feedbackMail(AppBuild(debug = false, version = "0.1.0-rc.10 (10010)", device = "Google Pixel 9a, Android 15"))

        assertTrue(mail.startsWith("mailto:$FEEDBACK_ADDRESS?subject="), mail)
        assertTrue("0.1.0-rc.10%20%2810010%29" in mail, mail)
        assertTrue("Google%20Pixel%209a%2C%20Android%2015" in mail, mail)
    }

    @Test
    fun `the form link carries the version and the phone and the app language`() {
        val form = feedbackForm(AppBuild(debug = false, version = "0.8.0 (800)", device = "Pixel 7, Android 16"), Language.Burmese)

        assertEquals("$FEEDBACK_FORM?version=0.8.0%20%28800%29&device=Pixel%207%2C%20Android%2016&lang=my", form)
    }

    /** A mail app reads a raw space, line break or Burmese letter in a link in its own way, or not at all. */
    @Test
    fun `spaces and line breaks and Burmese letters are sent as percent bytes`() {
        assertEquals("a%20b%0Ac", "a b\nc".percentEncoded())
        assertEquals("%E1%80%80", "က".percentEncoded())
        assertEquals("A-z_0.9~", "A-z_0.9~".percentEncoded())
    }
}
