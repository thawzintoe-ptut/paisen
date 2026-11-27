package com.ptut.expense.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitRulesTest {

    private val group = SharedGroup(
        id = "g1",
        name = "Trip",
        type = GroupType.Trip,
        icon = "work",
        simplifyDebts = true,
        createdAtEpochMillis = 0,
        meMemberId = "me",
        members = listOf(
            Member("me", "g1", "You", personId = null),
            Member("aye", "g1", "Aye", personId = null),
            Member("bo", "g1", "Bo", personId = null),
            Member("dan", "g1", "Dan", personId = null),
        ),
    )

    @Test
    fun anEqualSplitBeingEditedStaysBetweenItsOwnPeople() {
        val members = group.membersToSplit(SplitMode.Equally, sharers = setOf("me", "aye", "bo"))

        assertEquals(listOf("aye", "bo", "me"), members.map { it.id }.sorted())
    }

    @Test
    fun aNewEqualSplitIsBetweenEveryone() {
        assertEquals(4, group.membersToSplit(SplitMode.Equally, sharers = emptySet()).size)
    }

    @Test
    fun aSharesSplitBeingEditedOffersEveryoneSoANewMemberCanBeAdded() {
        assertEquals(4, group.membersToSplit(SplitMode.Shares, sharers = setOf("me", "aye")).size)
    }

    @Test
    fun aHalfTypedPercentOfAHugeTotalNeverShowsANegativePart() {
        val check = split(Long.MAX_VALUE / 2, SplitMode.Percent, group.splitMembers(), mapOf("me" to 9_999L))

        assertTrue(check.parts.values.all { it >= 0 }, "${check.parts}")
    }

    @Test
    fun anAmountTooLargeToConvertReadsAsNoAmount() {
        assertNull(convertOrNull(100_000_000_000_000_000L, Currencies.of("USD"), Rate.parse("9".repeat(11)), Currencies.Thb))
    }

    @Test
    fun aNegativeTotalSplitsToTheLastSatang() {
        val members = group.membersToSplit(SplitMode.Equally, sharers = setOf("me", "aye", "bo"))

        val parts = allocate(-100, members.associate { it.id to 1L }, members)

        assertEquals(-100, parts.values.sum())
    }

    @Test
    fun sharesTooLargeToAddUpSplitNothingAndCannotSave() {
        val members = group.membersToSplit(SplitMode.Shares, sharers = setOf("me", "aye"))

        val check = split(1_000, SplitMode.Shares, members, mapOf("me" to Long.MAX_VALUE, "aye" to Long.MAX_VALUE))

        assertTrue(check.parts.isEmpty())
        assertTrue(!check.complete)
    }
}
