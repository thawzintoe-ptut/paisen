package com.ptut.expense.shared

import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.groupDetail
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.BurmeseSharedDetail
import com.ptut.expense.sync.GroupSyncStatus
import com.ptut.expense.sync.SyncHealth
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupDetailMapperTest {

    private val mapper = GroupDetailMapper(categories = emptyList())

    @Test
    fun `settle up names the largest debt that involves you`() {
        // You owe Aye 300, and Mali owes you 150.
        val state = loaded(
            expense("rent", payer = "aye", "me" to 30_000, "aye" to 30_000, "mali" to 30_000),
            expense("snacks", payer = "me", "me" to 15_000, "mali" to 15_000),
        )

        val settle = assertNotNull(state.settleUp)
        assertEquals("me", settle.fromId)
        assertEquals("aye", settle.toId)
        assertTrue(settle.line.startsWith("with Aye"))
        assertTrue(settle.line.contains("300"))
        assertNull(state.balanceChip)
        assertTrue(state.hasActivity)
    }

    @Test
    fun `settle up is hidden and the chip says all settled up when everyone is square`() {
        val state = loaded(expense("lunch", payer = "me", "me" to 20_000))

        assertNull(state.settleUp)
        assertEquals(AppText.copy.shared.detail.allSettledUp, state.balanceChip)
    }

    @Test
    fun `a group with no expenses says nothing to settle yet`() {
        val state = loaded()

        assertNull(state.settleUp)
        assertEquals(AppText.copy.shared.detail.nothingToSettle, state.balanceChip)
        assertFalse(state.hasActivity)
        assertEquals(AppText.copy.shared.detail.nothingSpent("THB"), state.spendLine)
    }

    @Test
    fun `a group of one says just you so far`() {
        val alone = flatOfThree.copy(members = flatOfThree.members.take(1))
        val state = loaded(group = alone)

        assertEquals(AppText.copy.shared.detail.justYou, state.memberLine)
    }

    @Test
    fun `a twelve person group shows you and two others then plus nine`() {
        val big = flatOfThree.copy(members = flatOfThree.members + (1..9).map { Member(id = "m$it", groupId = "g1", name = "Person $it", personId = null) })
        val state = loaded(group = big)

        assertEquals(12, state.members.size)
        assertEquals(3, state.headerMembers.size)
        assertTrue(state.headerMembers.first().isMe)
        val more = assertNotNull(state.moreMembers)
        assertEquals("+9", more.label)
        assertEquals("9 more people", more.description)
    }

    @Test
    fun `a group of three shows every avatar and no count`() {
        val state = loaded()

        assertEquals(state.members, state.headerMembers)
        assertNull(state.moreMembers)
    }

    @Test
    fun `the Burmese count uses Burmese digits`() {
        assertEquals("+၉", BurmeseSharedDetail.moreAvatars(9))
        assertTrue(BurmeseSharedDetail.morePeople(9).contains("၉"))
    }

    @Test
    fun `try again shows only while changes wait or the last sync failed`() {
        assertTrue(GroupSyncStatus(SyncHealth.Offline, 0).offersRetry())
        assertTrue(GroupSyncStatus(SyncHealth.Waiting, 3).offersRetry())
        assertFalse(GroupSyncStatus(SyncHealth.UpToDate, 0).offersRetry())
        assertFalse(GroupSyncStatus(SyncHealth.Local, 0).offersRetry())
        assertFalse(GroupSyncStatus(SyncHealth.SignedOut, 2).offersRetry())
    }

    @Test
    fun `a group only on this phone never offers Try again`() {
        assertFalse(loaded().canRetrySync)
    }

    @Test
    fun `the same day six years apart reads alike and stays two days`() {
        val state = loaded(
            expense("then", payer = "me", "me" to 100, createdAt = noon(2020)),
            expense("now", payer = "me", "me" to 100, createdAt = noon(2026)),
        )

        assertEquals(1, state.days.map { it.label }.distinct().size)
        assertEquals(listOf(LocalDate(2026, 11, 12), LocalDate(2020, 11, 12)), state.days.map { it.date })
    }

    /** Hick's law: five entries in the menu, and what changes the group one step down. */
    @Test
    fun `the menu has four rows under Invite and Group settings holds the rest`() {
        val state = loaded()

        assertEquals(
            listOf(GroupMenuEntry.WhoPaysWhom, GroupMenuEntry.RepeatAnExpense, GroupMenuEntry.Activity, GroupMenuEntry.GroupSettings),
            state.menuEntries(),
        )
        assertEquals(listOf(GroupMenuEntry.WhoPaysWhom, GroupMenuEntry.Activity, GroupMenuEntry.GroupSettings), state.menuEntries(offersRepeat = false))
        assertEquals(
            listOf(GroupSettingsEntry.EditGroup, GroupSettingsEntry.MyPromptPay, GroupSettingsEntry.AddPeople, GroupSettingsEntry.RemoveSomeone, GroupSettingsEntry.LeaveOrDelete),
            state.settingsEntries(),
        )
        assertEquals(
            listOf(GroupSettingsEntry.EditGroup, GroupSettingsEntry.MyPromptPay, GroupSettingsEntry.AddPeople, GroupSettingsEntry.LeaveOrDelete),
            state.copy(canRemovePeople = false).settingsEntries(),
        )
    }

    private fun loaded(vararg expenses: SharedExpense, group: SharedGroup = flatOfThree): GroupDetailUiState.Loaded {
        val snapshot = SharedSnapshot(groups = listOf(group), people = emptyList(), expenseVersions = expenses.toList(), payments = emptyList(), links = emptyMap())
        val detail = requireNotNull(snapshot.groupDetail(group.id, seen = SEEN, today = NOW.dateIn(HomeTime)))
        return mapper.loaded(detail, status = GroupSyncStatus(SyncHealth.Local, 0), syncAvailable = false)
    }
}
