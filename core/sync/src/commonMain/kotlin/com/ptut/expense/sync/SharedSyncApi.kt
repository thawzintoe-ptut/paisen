package com.ptut.expense.sync

import kotlinx.coroutines.flow.Flow

/**
 * What the domain asks of shared sync. [SharedSync] is the one real answer; tests give a small
 * fake instead of a whole Firestore, so Join, Sign in and Account can be checked on their own.
 */
/** A change a group would not take. [title] is blank and [amountMinor] null when it was not an expense or a payment. */
data class RefusedChange(
    val id: String,
    val groupId: String,
    val groupName: String?,
    val title: String,
    val amountMinor: Long?,
    val currency: String?,
)

interface SharedSyncApi {
    /** False in a build that names no Firebase project. */
    val available: Boolean
    val signedIn: Flow<Boolean>
    val isSignedIn: Boolean

    fun status(groupId: String): Flow<GroupSyncStatus>
    fun overallStatus(): Flow<GroupSyncStatus>
    fun acknowledgeRefused(groupId: String)

    /** Changes a group refused for good, kept until read; a member removed while offline finds theirs here. */
    val refusedChanges: Flow<List<RefusedChange>>
    suspend fun dismissRefused(id: String)

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
