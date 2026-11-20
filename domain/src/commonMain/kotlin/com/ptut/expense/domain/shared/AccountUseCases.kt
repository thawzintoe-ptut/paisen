package com.ptut.expense.domain.shared

import com.ptut.expense.sync.ChangesWaiting
import com.ptut.expense.sync.SharedSync
import com.ptut.expense.sync.SyncProblem
import com.ptut.expense.sync.auth.Account
import com.ptut.expense.sync.auth.AuthRepository
import com.ptut.expense.sync.auth.ProviderToken
import com.ptut.expense.sync.auth.SignInCancelled
import com.ptut.expense.sync.syncProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** #187: who this phone shares as, as it changes; null in a build that names no Firebase project. */
class ObserveAccountUseCase(private val auth: AuthRepository? = null) {
    operator fun invoke(): Flow<Account?> = auth?.takeIf { it.available }?.account ?: flowOf(null)
}

/** How a sign-in ended (#187). */
sealed interface SignInResult {
    data object SignedIn : SignInResult

    /** The person closed the Google or Apple sheet, or this build has no sign-in: nothing to say. */
    data object Cancelled : SignInResult

    data object Offline : SignInResult

    data object Failed : SignInResult
}

/** #187: turns the phone's Google or Apple token, from [obtain], into a Firebase account. */
class SignInUseCase(private val auth: AuthRepository? = null) {
    suspend operator fun invoke(obtain: suspend () -> ProviderToken): SignInResult {
        val auth = auth ?: return SignInResult.Cancelled
        return try {
            auth.signIn(obtain())
            SignInResult.SignedIn
        } catch (_: SignInCancelled) {
            SignInResult.Cancelled
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.syncProblem() == SyncProblem.Offline) SignInResult.Offline else SignInResult.Failed
        }
    }
}

/** How signing out or deleting the account ended (#187). */
sealed interface AccountExit {
    data object Done : AccountExit

    /** [count] changes have not reached their groups, and leaving now would lose them. */
    data class Waiting(val count: Int) : AccountExit

    /** Firebase deletes only after a recent sign-in, so the person signs in once more. */
    data object NeedsFreshSignIn : AccountExit

    data class Failed(val cause: Exception) : AccountExit
}

/** #187: shared groups leave this phone with the account and come back with it; your own spending stays. */
class SignOutUseCase(
    private val auth: AuthRepository? = null,
    private val sync: SharedSync? = null,
) {
    suspend operator fun invoke(): AccountExit = accountExit { if (sync != null) sync.signOut() else auth?.signOut() }
}

/** Apple 5.1.1(v): leaves every shared group, then removes the account. */
class DeleteAccountUseCase(
    private val auth: AuthRepository? = null,
    private val sync: SharedSync? = null,
) {
    suspend operator fun invoke(): AccountExit = accountExit { if (sync != null) sync.deleteAccount() else auth?.deleteAccount() }
}

private suspend fun accountExit(block: suspend () -> Unit): AccountExit = try {
    block()
    AccountExit.Done
} catch (waiting: ChangesWaiting) {
    AccountExit.Waiting(waiting.count)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    if (failure.syncProblem() == SyncProblem.NeedsFreshSignIn) AccountExit.NeedsFreshSignIn else AccountExit.Failed(failure)
}
