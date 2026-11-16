package com.ptut.expense.sync.push

import com.ptut.expense.locale.Language
import kotlin.test.Test
import kotlin.test.assertEquals

class PushNoticesTest {

    @Test
    fun eachPhoneGetsTheSentenceInItsOwnLanguage() {
        val notice = PushNotices(groupName = "Trip", actor = "Mali").joined()

        assertEquals(Language.entries.map { it.copy.shared.push.joined("Mali") }, Language.entries.map(notice::body))
    }

    /** A language with no push words of its own would quietly send another language's sentence. */
    @Test
    fun noTwoLanguagesShareASentence() {
        val notice = PushNotices(groupName = "Trip", actor = "Mali").joined()

        assertEquals(Language.entries.size, Language.entries.map(notice::body).toSet().size)
    }
}
