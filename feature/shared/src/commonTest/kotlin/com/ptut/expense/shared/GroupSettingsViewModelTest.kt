package com.ptut.expense.shared

import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.shared.GroupType
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupSettingsViewModelTest : ViewModelTest() {

    @Test
    fun `a group only on this phone is deleted here and the screen leaves`() = runTest {
        val repository = FakeSharedRepository()
        val viewModel = GroupSettingsViewModel(groupId = "g1", repository = repository)

        viewModel.removeGroup()
        runCurrent()

        assertEquals(listOf("g1"), repository.deletedGroups)
        assertTrue(viewModel.isRemoved.value)
        assertEquals(GroupActionState(), viewModel.groupActionState.value)
    }

    @Test
    fun `a group that has gone already asks nothing of the repository`() = runTest {
        val repository = FakeSharedRepository()
        val viewModel = GroupSettingsViewModel(groupId = "gone", repository = repository)

        viewModel.removeGroup()
        runCurrent()

        assertTrue(repository.deletedGroups.isEmpty())
        assertFalse(viewModel.isRemoved.value)
    }

    @Test
    fun `a blank name leaves the group as it was`() = runTest {
        val repository = FakeSharedRepository()
        val viewModel = GroupSettingsViewModel(groupId = "g1", repository = repository)

        viewModel.editGroup("  ", GroupType.Flat, "home")
        viewModel.editGroup("Flat 14", GroupType.Flat, "home")
        runCurrent()

        assertEquals(listOf("Flat 14"), repository.editedGroups)
    }

    @Test
    fun `an invite in a build without sync ends idle`() = runTest {
        val viewModel = GroupSettingsViewModel(groupId = "g1", repository = FakeSharedRepository())

        viewModel.invite()
        runCurrent()

        assertEquals(InviteActionState(), viewModel.inviteAction.value)
    }
}
