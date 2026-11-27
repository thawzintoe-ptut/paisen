package com.ptut.expense.domain.shared

import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.sync.JoinChoice
import com.ptut.expense.sync.SharedSyncApi

/** #190: shares the group the first time, then keeps its link. The caller has signed in; without sync it does nothing. */
class ShareGroupUseCase(private val sync: SharedSyncApi? = null) {
    suspend operator fun invoke(groupId: String): Result<Unit> = runSuspendCatching { sync?.share(groupId) }.map { }
}

/** #190: a fresh invite code; the old one stops working. Only the creator may. */
class RenewInviteUseCase(private val sync: SharedSyncApi? = null) {
    suspend operator fun invoke(groupId: String): Result<Unit> = runSuspendCatching { sync?.newInvite(groupId) }.map { }
}

/** #190: "This invite has expired" from now on, for anyone who still has the link. */
class SwitchOffInviteUseCase(private val sync: SharedSyncApi? = null) {
    suspend operator fun invoke(groupId: String): Result<Unit> = runSuspendCatching { sync?.switchOffInvite(groupId) }
}

/** #190 step one: the group behind an invite and the names nobody has claimed yet. */
class OpenInviteUseCase(private val sync: SharedSyncApi? = null) {
    suspend operator fun invoke(code: String): Result<JoinChoice> = runSuspendCatching { requireNotNull(sync) { NO_SYNC }.openJoin(code) }
}

/** #190 step two: claims [memberId], or adds the account's own name when it is null. The group's id. */
class JoinGroupUseCase(private val sync: SharedSyncApi? = null) {
    suspend operator fun invoke(choice: JoinChoice, memberId: String?): Result<String> =
        runSuspendCatching { requireNotNull(sync) { NO_SYNC }.join(choice, memberId) }
}

private const val NO_SYNC = "No sync in this build"
