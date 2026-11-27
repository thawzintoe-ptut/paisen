package com.ptut.expense.sync

import kotlinx.coroutines.flow.Flow

/**
 * What the domain asks of shared sync. [SharedSync] is the one real answer; tests give a small
 * fake instead of a whole Firestore, so Join, Sign in and Account can be checked on their own.
 */
interface SharedSyncApi {
    /** False in a build that names no Firebase project. */
    val available: Boolean
    val signedIn: Flow<Boolean>
    val isSignedIn: Boolean

    fun status(groupId: String): Flow<GroupSyncStatus>
    fun overallStatus(): Flow<GroupSyncStatus>
    fun acknowledgeRefused(groupId: String)

    suspend fun syncAll(): Boolean
    suspend fun syncAllOutcome(withReactions: Boolean = true): SyncOutcome
    suspend fun syncGroupOutcome(groupId: String, withReactions: Boolean = true): SyncOutcome

    suspend fun share(groupId: String): String
    suspend fun newInvite(groupId: String): String
    suspend fun switchOffInvite(groupId: String)
    suspend fun openJoin(code: String): JoinChoice
    suspend fun join(choice: JoinChoice, memberId: String?): String

    suspend fun leaveGroup(groupId: String, dropUnsent: Boolean = false)
    suspend fun deleteForEveryone(groupId: String)
    suspend fun signOut()
    suspend fun deleteAccount()
}
