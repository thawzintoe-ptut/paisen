package com.ptut.expense.sync

/** What a sync does about the account being signed in on other phones. */
internal enum class SessionVerdict { Claim, Keep, EndHere }

/**
 * One phone at a time: the newest sign-in wins. [onServer] is the sign-in the account last stored,
 * [mine] this phone's; both are `auth_time` seconds.
 */
internal fun sessionVerdict(onServer: Long?, mine: Long): SessionVerdict = when {
    onServer == null || onServer < mine -> SessionVerdict.Claim
    onServer == mine -> SessionVerdict.Keep
    else -> SessionVerdict.EndHere
}

/** Groups the account is in that this phone does not hold, leaving out ones deleted for everyone. */
internal fun groupsToRestore(mine: List<FirestoreDoc>, onPhone: Set<String>): List<String> =
    mine.filter { it.id !in onPhone && !it.fields.bool("deleted") }.map { it.id }

/** The name [uid] claimed in a group, so a group pulled onto this phone knows which one is "you". */
internal fun claimedBy(members: List<FirestoreDoc>, uid: String): String? =
    members.firstOrNull { it.fields.string("claimedByUid") == uid }?.id
