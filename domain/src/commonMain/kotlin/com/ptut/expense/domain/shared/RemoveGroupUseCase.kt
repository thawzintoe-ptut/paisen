package com.ptut.expense.domain.shared

import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.sync.ChangesWaiting
import com.ptut.expense.sync.SharedSync
import kotlinx.coroutines.CancellationException

/** The two ways a shared group leaves this phone through the server (#218). */
interface GroupExits {
    suspend fun deleteForEveryone(groupId: String)

    /** [dropUnsent] leaves even with changes that could not be sent. */
    suspend fun leave(groupId: String, dropUnsent: Boolean)
}

/** [GroupExits] as [SharedSync] does them. */
class SharedSyncExits(private val sync: SharedSync) : GroupExits {
    override suspend fun deleteForEveryone(groupId: String) = sync.deleteForEveryone(groupId)

    override suspend fun leave(groupId: String, dropUnsent: Boolean) = sync.leaveGroup(groupId, dropUnsent = dropUnsent)
}

sealed interface RemoveGroupResult {
    data object Removed : RemoveGroupResult

    /** The group had already gone from this phone; nothing was asked of anyone. */
    data object Gone : RemoveGroupResult

    /** Leaving would drop [count] changes that have not reached the server; asked again before they go. */
    data class Waiting(val count: Int) : RemoveGroupResult

    data class Failed(val cause: Exception) : RemoveGroupResult
}

/**
 * #218: a group only on this phone is deleted here; a shared one is deleted by its creator and left by others
 * ([removal]). Reminders are lined up again after; one that fails does not undo the removal.
 */
class RemoveGroupUseCase(
    private val repository: SharedRepository,
    private val exits: GroupExits? = null,
    private val runDueRepeats: RunDueRepeatsUseCase? = null,
) {
    suspend operator fun invoke(groupId: String, dropUnsent: Boolean): RemoveGroupResult {
        val group = repository.snapshot.value.group(groupId) ?: return RemoveGroupResult.Gone
        return try {
            when (group.removal()) {
                GroupRemoval.DeleteHere -> repository.deleteGroup(groupId)
                GroupRemoval.DeleteForEveryone -> requireNotNull(exits) { "No sync in this build" }.deleteForEveryone(groupId)
                GroupRemoval.Leave -> requireNotNull(exits) { "No sync in this build" }.leave(groupId, dropUnsent)
            }
            runSuspendCatching { runDueRepeats?.invoke() }
            RemoveGroupResult.Removed
        } catch (waiting: ChangesWaiting) {
            RemoveGroupResult.Waiting(waiting.count)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            RemoveGroupResult.Failed(failure)
        }
    }
}
