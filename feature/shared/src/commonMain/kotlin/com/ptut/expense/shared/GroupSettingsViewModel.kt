package com.ptut.expense.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.sync.ChangesWaiting
import com.ptut.expense.sync.SharedSync
import com.ptut.expense.sync.syncMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The group menu's actions: invite (#190), Edit group, Add people and Remove someone (#218), and
 * delete or leave. Each has its own state beside the group screen's, which never waits on these.
 */
class GroupSettingsViewModel(
    private val groupId: String,
    private val repository: SharedRepository,
    private val sync: SharedSync? = null,
    /** #203: rebuilds repeat reminders once a group has gone from this phone. */
    private val runner: RepeatRunner? = null,
) : ViewModel() {

    private val action = MutableStateFlow(InviteActionState())
    val inviteAction: StateFlow<InviteActionState> = action

    private val groupAction = MutableStateFlow(GroupActionState())
    val groupActionState: StateFlow<GroupActionState> = groupAction

    private val removed = MutableStateFlow(false)

    /** True once the group has gone from this phone; the shell leaves the screen on it. */
    val isRemoved: StateFlow<Boolean> = removed

    fun editGroup(name: String, type: GroupType, icon: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.editGroup(groupId, name, type, icon) }
    }

    /**
     * Asked first in a sheet; there is no undo, so the sheet is the safeguard. Leave sends what waits
     * first and asks again before [anyway] drops what still could not go (#218).
     */
    fun removeGroup(anyway: Boolean = false) {
        val group = repository.snapshot.value.group(groupId) ?: return
        viewModelScope.launch {
            groupAction.value = GroupActionState(working = true)
            groupAction.value = try {
                when (group.removal()) {
                    GroupRemoval.DeleteHere -> repository.deleteGroup(groupId)
                    GroupRemoval.DeleteForEveryone -> requireNotNull(sync).deleteForEveryone(groupId)
                    GroupRemoval.Leave -> requireNotNull(sync).leaveGroup(groupId, dropUnsent = anyway)
                }
                runSuspendCatching { runner?.run() }
                removed.value = true
                GroupActionState()
            } catch (waiting: ChangesWaiting) {
                GroupActionState(waiting = waiting.count)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                GroupActionState(error = failure.syncMessage())
            }
        }
    }

    /** #218: names added after Create. In a shared group they sync, and each joins from the link. */
    fun addPeople(members: List<NewMember>) {
        if (members.none { it.name.isNotBlank() }) return
        viewModelScope.launch { repository.addMembers(groupId, members) }
    }

    /** #218: archives one name here; past splits keep it. The repository holds the creator-only rule. */
    fun removeMember(memberId: String) {
        viewModelScope.launch { repository.removeMember(groupId, memberId) }
    }

    fun dismissGroupError() {
        groupAction.value = GroupActionState()
    }

    /** Shares the group the first time, then shows its link (#190). The caller has signed in. */
    fun invite() = runInvite { sync?.share(groupId) }

    fun newLink() = runInvite { sync?.newInvite(groupId) }

    fun switchOff() = runInvite { sync?.switchOffInvite(groupId) }

    private fun runInvite(work: suspend () -> Unit) {
        viewModelScope.launch {
            action.value = InviteActionState(working = true)
            action.value = runSuspendCatching { work() }.fold(
                onSuccess = { InviteActionState() },
                onFailure = { failure -> InviteActionState(error = failure.syncMessage()) },
            )
        }
    }
}
