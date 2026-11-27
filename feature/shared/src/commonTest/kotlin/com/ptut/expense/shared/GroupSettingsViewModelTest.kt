package com.ptut.expense.shared

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.first
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ViewModelTest
import com.ptut.expense.domain.shared.AddMembersUseCase
import com.ptut.expense.domain.shared.EditGroupUseCase
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.RemoveGroupUseCase
import com.ptut.expense.domain.shared.RemoveMemberUseCase
import com.ptut.expense.domain.shared.RenewInviteUseCase
import com.ptut.expense.domain.shared.ShareGroupUseCase
import com.ptut.expense.domain.shared.SwitchOffInviteUseCase
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
        val viewModel = groupSettingsViewModel(groupId = "g1", repository = repository)

        viewModel.removeGroup()
        runCurrent()

        assertEquals(listOf("g1"), repository.deletedGroups)
        assertTrue(viewModel.isRemoved.value)
        assertEquals(GroupActionState(), viewModel.groupActionState.value)
    }

    @Test
    fun `a group that has gone already asks nothing of the repository`() = runTest {
        val repository = FakeSharedRepository()
        val viewModel = groupSettingsViewModel(groupId = "gone", repository = repository)

        viewModel.removeGroup()
        runCurrent()

        assertTrue(repository.deletedGroups.isEmpty())
        assertFalse(viewModel.isRemoved.value)
    }

    @Test
    fun `a blank name leaves the group as it was`() = runTest {
        val repository = FakeSharedRepository()
        val viewModel = groupSettingsViewModel(groupId = "g1", repository = repository)

        viewModel.editGroup("  ", GroupType.Flat, "home")
        viewModel.editGroup("Flat 14", GroupType.Flat, "home")
        runCurrent()

        assertEquals(listOf("Flat 14"), repository.editedGroups)
    }

    @Test
    fun `an invite in a build without sync ends idle`() = runTest {
        val viewModel = groupSettingsViewModel(groupId = "g1", repository = FakeSharedRepository())

        viewModel.invite()
        runCurrent()

        assertEquals(InviteActionState(), viewModel.inviteAction.value)
    }

    @Test
    fun `two fast taps on Delete group delete it once`() = runTest {
        val repository = FakeSharedRepository()
        val viewModel = groupSettingsViewModel(groupId = "g1", repository = repository)

        viewModel.removeGroup()
        viewModel.removeGroup()
        runCurrent()

        assertEquals(listOf("g1"), repository.deletedGroups)
    }

    @Test
    fun `a failed rename says so`() = runTest {
        val repository = FakeSharedRepository().apply { failNextEdit = true }
        val toasts = ToastMessages()
        val viewModel = groupSettingsViewModel(groupId = "g1", repository = repository, toasts = toasts)

        viewModel.editGroup("Flat 14", GroupType.Flat, "home")
        runCurrent()

        assertEquals(AppText.copy.common.notDone, toasts.messages.first().text)
    }

    @Test
    fun `removing a member says who went and a failed one says so`() = runTest {
        val repository = FakeSharedRepository()
        val toasts = ToastMessages()
        val viewModel = groupSettingsViewModel(groupId = "g1", repository = repository, toasts = toasts)

        viewModel.removeMember("m2", "Aye")
        runCurrent()
        repository.failNextRemoveMember = true
        viewModel.removeMember("m3", "Bo")
        runCurrent()

        assertEquals(listOf("m2"), repository.removedMembers)
        assertEquals(listOf(AppText.copy.common.removedName("Aye"), AppText.copy.common.notDone), toasts.messages.take(2).toList().map { it.text })
    }
}

/** The group menu as Koin builds it, its use cases over [repository], in a build without sync. */
private fun groupSettingsViewModel(groupId: String, repository: FakeSharedRepository, toasts: ToastMessages? = null) = GroupSettingsViewModel(
    groupId = groupId,
    toasts = toasts,
    editGroupUseCase = EditGroupUseCase(repository),
    removeGroupUseCase = RemoveGroupUseCase(repository),
    addMembers = AddMembersUseCase(repository),
    removeMemberUseCase = RemoveMemberUseCase(repository),
    shareGroup = ShareGroupUseCase(),
    renewInvite = RenewInviteUseCase(),
    switchOffInvite = SwitchOffInviteUseCase(),
)
