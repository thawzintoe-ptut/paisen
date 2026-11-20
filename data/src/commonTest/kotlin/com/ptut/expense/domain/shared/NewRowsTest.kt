package com.ptut.expense.domain.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewRowsTest {

    private val group = SharedGroup(
        id = "g1",
        name = "Flat 12",
        type = GroupType.Flat,
        icon = "home",
        simplifyDebts = true,
        createdAtEpochMillis = 0,
        meMemberId = "me",
        members = listOf(
            Member(id = "me", groupId = "g1", name = "Ko Ko", personId = null),
            Member(id = "aye", groupId = "g1", name = "Aye", personId = null),
        ),
    )

    @Test
    fun anotherMembersRowAfterTheMarkIsNew() {
        assertTrue(group.isNewToMe(author = "aye", atEpochMillis = 2_000, mark = 1_000))
    }

    @Test
    fun aRowAtOrBeforeTheMarkIsNotNew() {
        assertFalse(group.isNewToMe(author = "aye", atEpochMillis = 1_000, mark = 1_000))
        assertFalse(group.isNewToMe(author = "aye", atEpochMillis = 500, mark = 1_000))
    }

    @Test
    fun thisPhonesOwnRowIsNeverNew() {
        assertFalse(group.isNewToMe(author = "me", atEpochMillis = 2_000, mark = 1_000))
    }

    @Test
    fun nothingIsNewOnAFirstVisitOrFromNoAuthor() {
        assertFalse(group.isNewToMe(author = "aye", atEpochMillis = 2_000, mark = null))
        assertFalse(group.isNewToMe(author = null, atEpochMillis = 2_000, mark = 1_000))
    }
}
