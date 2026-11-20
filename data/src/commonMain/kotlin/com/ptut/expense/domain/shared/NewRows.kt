package com.ptut.expense.domain.shared

/** The newest expense version or payment in [groupId], by anyone: how far a visit has shown. */
fun SharedSnapshot.newestRowAt(groupId: String): Long? =
    (
        expenseVersions.filter { it.groupId == groupId }.map { it.createdAtEpochMillis } +
            payments.filter { it.groupId == groupId }.map { it.createdAtEpochMillis }
        )
        .maxOrNull()

/**
 * Research finding 5: who wrote each expense version or payment after [SharedGroup.lastSeenAtEpochMillis],
 * other members only. Empty before the group's first visit, when everything would count.
 */
fun SharedSnapshot.newFromOthers(group: SharedGroup): List<String> {
    val seen = group.lastSeenAtEpochMillis ?: return emptyList()
    val written = expenseVersions.filter { it.groupId == group.id }.map { it.authorMemberId to it.createdAtEpochMillis } +
        payments.filter { it.groupId == group.id }.map { it.authorMemberId to it.createdAtEpochMillis }
    return written.mapNotNull { (author, at) -> author?.takeIf { !group.isMe(it) && at > seen } }
}

/** Another member's row after [mark]. Never on a first visit, when everything would be new, nor for this phone's own rows. */
fun SharedGroup.isNewToMe(author: String?, atEpochMillis: Long, mark: Long?): Boolean =
    mark != null && author != null && !isMe(author) && atEpochMillis > mark
