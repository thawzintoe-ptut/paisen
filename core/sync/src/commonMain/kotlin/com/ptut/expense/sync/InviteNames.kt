package com.ptut.expense.sync

import com.ptut.expense.data.local.entity.GroupMemberEntity
import com.ptut.expense.locale.AppText

/** L4: the invite field listing the names nobody holds, as `{id, name}`, so opening a link writes nothing. */
internal const val INVITE_NAMES = "names"

/** The names someone joining may pick: claimed by nobody and not removed, oldest first, as many as the rules take. */
internal fun pickableNames(members: List<GroupMemberEntity>): List<Map<String, String>> = members
    .filter { it.claimedByUid == null && !it.archived }
    .sortedBy { it.createdAtEpochMillis }
    .take(MAX_INVITE_NAMES)
    .map { mapOf("id" to it.id, "name" to it.name) }

/** The invite's names, or null for an invite made by a build before L4, which carries none. */
internal fun invitedNames(invite: Fields): List<Pair<String, String>>? = (invite[INVITE_NAMES] as? List<*>)?.mapNotNull { entry ->
    val name = entry.asFields()
    val id = name.string("id") ?: return@mapNotNull null
    id to (name.string("name") ?: AppText.copy.shared.someone)
}

/** The rules' cap on an invite's names, the same as on a group's accounts. */
private const val MAX_INVITE_NAMES = 100
