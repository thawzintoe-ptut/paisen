package com.ptut.expense.domain.shared

/**
 * How a group goes. One only on this phone is deleted here; a shared one is deleted for everyone by
 * the account that created it, and left by everyone else, whose copy only goes from their phone.
 */
enum class GroupRemoval { DeleteHere, DeleteForEveryone, Leave }

/** The same rule the repository and Firestore's rules hold: only the creator removes a name. */
fun SharedGroup.createdHere(): Boolean {
    val myUid = member(meMemberId.orEmpty())?.claimedByUid
    return creatorUid != null && creatorUid == myUid
}

/** What the menu's last item does to this group, see [GroupRemoval]. */
fun SharedGroup.removal(): GroupRemoval = when {
    !remote -> GroupRemoval.DeleteHere
    createdHere() -> GroupRemoval.DeleteForEveryone
    else -> GroupRemoval.Leave
}
