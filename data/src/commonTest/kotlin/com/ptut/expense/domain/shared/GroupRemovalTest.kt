package com.ptut.expense.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupRemovalTest {

    private val group = SharedGroup(
        id = "g1",
        name = "Flat 12",
        type = GroupType.Flat,
        icon = "home",
        simplifyDebts = true,
        createdAtEpochMillis = 0,
        meMemberId = "me",
        members = listOf(
            Member(id = "me", groupId = "g1", name = "Ko Ko", personId = null, claimedByUid = "uid-me"),
            Member(id = "aye", groupId = "g1", name = "Aye", personId = null, claimedByUid = "uid-aye"),
        ),
    )

    @Test
    fun aGroupOnThisPhoneOnlyIsDeletedHere() {
        assertEquals(GroupRemoval.DeleteHere, group.removal())
    }

    @Test
    fun theCreatorOfASharedGroupDeletesItForEveryone() {
        val shared = group.copy(remote = true, creatorUid = "uid-me")

        assertTrue(shared.createdHere())
        assertEquals(GroupRemoval.DeleteForEveryone, shared.removal())
    }

    @Test
    fun anyoneElseLeavesASharedGroup() {
        val shared = group.copy(remote = true, creatorUid = "uid-aye")

        assertFalse(shared.createdHere())
        assertEquals(GroupRemoval.Leave, shared.removal())
    }

    @Test
    fun aGroupWithNoKnownCreatorIsNotCreatedHere() {
        val shared = group.copy(remote = true, creatorUid = null)

        assertFalse(shared.createdHere())
        assertEquals(GroupRemoval.Leave, shared.removal())
    }

    @Test
    fun aNameNobodyClaimedIsNotTheCreator() {
        val unclaimed = group.copy(
            remote = true,
            creatorUid = "uid-me",
            members = group.members.map { if (it.id == "me") it.copy(claimedByUid = null) else it },
        )

        assertFalse(unclaimed.createdHere())
    }
}
