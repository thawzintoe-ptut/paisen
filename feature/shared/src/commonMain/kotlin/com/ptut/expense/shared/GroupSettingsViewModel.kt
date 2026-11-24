package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.shared.AddMembersUseCase
import com.ptut.expense.domain.shared.EditGroupUseCase
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.RemoveGroupResult
import com.ptut.expense.domain.shared.RemoveGroupUseCase
import com.ptut.expense.domain.shared.RemoveMemberUseCase
import com.ptut.expense.domain.shared.RenewInviteUseCase
import com.ptut.expense.domain.shared.ShareGroupUseCase
import com.ptut.expense.domain.shared.SwitchOffInviteUseCase
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.syncMessage
import com.ptut.expense.ui.Haptics
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The group menu's actions: invite (#190), Edit group, Add people and Remove someone (#218), and
 * delete or leave. Each has its own state beside the group screen's, which never waits on these.
 */
class GroupSettingsViewModel(
    private val groupId: String,
    private val editGroupUseCase: EditGroupUseCase,
    private val removeGroupUseCase: RemoveGroupUseCase,
    private val addMembers: AddMembersUseCase,
    private val removeMemberUseCase: RemoveMemberUseCase,
    private val shareGroup: ShareGroupUseCase,
    private val renewInvite: RenewInviteUseCase,
    private val switchOffInvite: SwitchOffInviteUseCase,
    private val toasts: ToastMessages? = null,
    private val haptics: Haptics? = null,
) : ViewModel() {

    private val action = MutableStateFlow(InviteActionState())
    val inviteAction: StateFlow<InviteActionState> = action

    private val groupAction = MutableStateFlow(GroupActionState())
    val groupActionState: StateFlow<GroupActionState> = groupAction

    private val removed = MutableStateFlow(false)

    /** True once the group has gone from this phone; the shell leaves the screen on it. */
    val isRemoved: StateFlow<Boolean> = removed

    fun editGroup(name: String, type: GroupType, icon: String) {
        viewModelScope.launch { editGroupUseCase(groupId, name, type, icon) }
    }

    /**
     * Asked first in a sheet; there is no undo, so the sheet is the safeguard. Leave sends what waits
     * first and asks again before [anyway] drops what still could not go (#218).
     */
    fun removeGroup(anyway: Boolean = false) {
        viewModelScope.launch {
            groupAction.value = GroupActionState(working = true)
            groupAction.value = when (val result = removeGroupUseCase(groupId, dropUnsent = anyway)) {
                RemoveGroupResult.Removed -> {
                    removed.value = true
                    GroupActionState()
                }

                RemoveGroupResult.Gone -> GroupActionState()

                is RemoveGroupResult.Waiting -> GroupActionState(waiting = result.count)

                is RemoveGroupResult.Failed -> GroupActionState(error = result.cause.syncMessage())
            }
        }
    }

    /** #218: names added after Create. In a shared group they sync, and each joins from the link. */
    fun addPeople(members: List<NewMember>) {
        viewModelScope.launch { addMembers(groupId, members) }
    }

    /** #218: archives one name here; past splits keep it. No Undo: nothing puts an archived name back. */
    fun removeMember(memberId: String, name: String) {
        viewModelScope.launch {
            runSuspendCatching { removeMemberUseCase(groupId, memberId) }.fold(
                onSuccess = {
                    haptics?.deleted()
                    toasts?.show(ToastMessage(AppText.copy.common.removedName(name), tone = ToastTone.Deleted))
                },
                onFailure = { toasts?.show(ToastMessage(AppText.copy.common.notDone, tone = ToastTone.Error)) },
            )
        }
    }

    fun dismissGroupError() {
        groupAction.value = GroupActionState()
    }

    /** Shares the group the first time, then shows its link (#190). The caller has signed in. */
    fun invite() = runInvite { shareGroup(groupId) }

    fun newLink() = runInvite { renewInvite(groupId) }

    fun switchOff() = runInvite { switchOffInvite(groupId) }

    private fun runInvite(work: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            action.value = InviteActionState(working = true)
            action.value = work().fold(
                onSuccess = { InviteActionState() },
                onFailure = { failure -> InviteActionState(error = failure.syncMessage()) },
            )
        }
    }
}
