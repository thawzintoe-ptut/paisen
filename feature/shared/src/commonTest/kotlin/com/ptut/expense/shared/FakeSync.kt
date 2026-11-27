package com.ptut.expense.shared

import com.ptut.expense.sync.GroupSyncStatus
import com.ptut.expense.sync.JoinChoice
import com.ptut.expense.sync.RefusedChange
import com.ptut.expense.sync.SharedSyncApi
import com.ptut.expense.sync.SyncHealth
import com.ptut.expense.sync.SyncOutcome
import com.ptut.expense.sync.auth.Account
import com.ptut.expense.sync.auth.AuthRepository
import com.ptut.expense.sync.auth.ProviderToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** Shared sync as the screens see it: what an invite opens to, and what joining, signing out and deleting do. */
internal class FakeSync(signedIn: Boolean = true) : SharedSyncApi {
    val signedInNow = MutableStateFlow(signedIn)
    var choice: Result<JoinChoice> = Result.success(JoinChoice("g1", "Trip", names = listOf("m2" to "Aye")))
    val joins = mutableListOf<String?>()

    /** Holds join open, as a slow network would, so a second tap lands while the first runs. */
    var joinGate: CompletableDeferred<Unit>? = null
    var signOutFailure: Exception? = null
    var signOuts = 0

    override val available = true
    override val signedIn: Flow<Boolean> = signedInNow
    override val isSignedIn: Boolean get() = signedInNow.value

    override fun status(groupId: String): Flow<GroupSyncStatus> = flowOf(GroupSyncStatus(SyncHealth.UpToDate, 0))
    override fun overallStatus(): Flow<GroupSyncStatus> = status("")
    override fun acknowledgeRefused(groupId: String) = Unit
    override val refusedChanges = MutableStateFlow<List<RefusedChange>>(emptyList())
    override suspend fun dismissRefused(id: String) {
        refusedChanges.value = refusedChanges.value.filterNot { it.id == id }
    }
    override suspend fun syncAll(): Boolean = false
    override suspend fun syncAllOutcome(withReactions: Boolean): SyncOutcome = SyncOutcome(groupsChanged = false, failed = false)
    override suspend fun syncGroupOutcome(groupId: String, withReactions: Boolean): SyncOutcome = throw AssertionError("not in these tests")

    override suspend fun share(groupId: String): String = "CODE"
    override suspend fun newInvite(groupId: String): String = "CODE2"
    override suspend fun switchOffInvite(groupId: String) = Unit
    override suspend fun openJoin(code: String): JoinChoice = choice.getOrThrow()

    override suspend fun join(choice: JoinChoice, memberId: String?): String {
        joins += memberId
        joinGate?.await()
        return choice.groupId
    }

    override suspend fun leaveGroup(groupId: String, dropUnsent: Boolean) = Unit
    override suspend fun deleteForEveryone(groupId: String) = Unit

    override suspend fun signOut() {
        signOutFailure?.let { throw it }
        signOuts++
    }

    override suspend fun deleteAccount() = Unit
}

/** Sign-in as Firebase answers it: [answer] is what the next sign-in does. */
internal class FakeAuth(signedIn: Boolean = false) : AuthRepository {
    var answer: () -> Unit = {}
    override val available = true
    override val account = MutableStateFlow<Account>(if (signedIn) ME else Account.SignedOut)

    override suspend fun signIn(token: ProviderToken): Account.SignedIn {
        answer()
        account.value = ME
        return ME
    }

    override suspend fun idToken(): String = "token"
    override suspend fun signOut() {
        account.value = Account.SignedOut
    }

    override suspend fun deleteAccount() = signOut()

    companion object {
        val ME = Account.SignedIn("uid-1", "Thaw", "thaw@example.com", "google.com")
    }
}
