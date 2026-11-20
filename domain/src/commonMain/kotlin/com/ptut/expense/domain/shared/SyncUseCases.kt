package com.ptut.expense.domain.shared

import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.sync.GroupSyncStatus
import com.ptut.expense.sync.SharedSync
import com.ptut.expense.sync.SyncHealth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration.Companion.seconds

/** What a screen may offer around sharing (#187, #189). */
data class SyncAccess(
    /** False in a build that names no Firebase project: nothing syncs, and nothing asks to sign in. */
    val available: Boolean,
    /** Signed in right now, so a screen starts from the truth rather than from "signed out". */
    val signedInNow: Boolean,
    val signedIn: Flow<Boolean>,
)

/** Whether this build syncs, and whether this phone is signed in to share; never in a build without sync. */
class GetSyncAccessUseCase(private val sync: SharedSync? = null) {
    operator fun invoke(): SyncAccess = SyncAccess(
        available = sync?.available == true,
        signedInNow = sync?.isSignedIn == true,
        signedIn = sync?.signedIn ?: flowOf(false),
    )
}

/** #189, #196: one group's sync line, or every shared group's when [groupId] is null. Without sync, all stays on this phone. */
class ObserveSyncStatusUseCase(private val sync: SharedSync? = null) {
    operator fun invoke(groupId: String? = null): Flow<GroupSyncStatus> {
        val sync = sync ?: return flowOf(GroupSyncStatus(SyncHealth.Local, 0))
        return if (groupId == null) sync.overallStatus() else sync.status(groupId)
    }
}

/**
 * #196, #189: syncs while collected, every minute for the Shared tab and every 15 seconds for one open group, since iOS
 * has no push yet. It emits at once; a build without sync emits only that. Back-off: [syncPolling].
 */
class PollSyncUseCase(
    private val sync: SharedSync? = null,
    /** #203: rebuilds repeat reminders once a group has gone from this phone. */
    private val runDueRepeats: RunDueRepeatsUseCase? = null,
) {
    operator fun invoke(groupId: String? = null): Flow<Unit> {
        val sync = sync?.takeIf { it.available } ?: return flowOf(Unit)
        return if (groupId == null) {
            syncPolling(sync.signedIn, runDueRepeats, ALL_GROUPS_TICK, sync::syncAllOutcome)
        } else {
            syncPolling(sync.signedIn, runDueRepeats, ONE_GROUP_TICK) { sync.syncGroupOutcome(groupId) }
        }
    }

    private companion object {
        val ALL_GROUPS_TICK = 60.seconds
        val ONE_GROUP_TICK = 15.seconds
    }
}

/** Pull-to-refresh: syncs one group now instead of at the next tick. False when it failed or this build has no sync. */
class SyncGroupNowUseCase(
    private val sync: SharedSync? = null,
    private val runDueRepeats: RunDueRepeatsUseCase? = null,
) {
    suspend operator fun invoke(groupId: String): Boolean {
        val sync = sync?.takeIf { it.available } ?: return false
        return syncOnce(runDueRepeats) { sync.syncGroupOutcome(groupId) }
    }
}

/** Every shared group now, as right after signing in; a failure waits for the next sync. */
class SyncAllNowUseCase(private val sync: SharedSync? = null) {
    suspend operator fun invoke() {
        runSuspendCatching { sync?.syncAll() }
    }
}

/** #194: the group screen has said how many changes were refused; the count starts again from zero. */
class AcknowledgeRefusedUseCase(private val sync: SharedSync? = null) {
    operator fun invoke(groupId: String) {
        sync?.acknowledgeRefused(groupId)
    }
}
