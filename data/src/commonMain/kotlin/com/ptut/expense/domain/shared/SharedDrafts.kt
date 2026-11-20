package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang

/** The newest version of an expense, a removed one too; null when it was never in [groupId]. */
fun SharedSnapshot.latestVersionOf(groupId: String, expenseId: String): SharedExpense? =
    expenseVersions.filter { it.groupId == groupId && it.id == expenseId }.maxWithOrNull(LogOrder)

/**
 * The people a split covers: those still in [group], plus everyone with a part in [expenseId]'s version on an edit.
 * An edit of an Equally split keeps only those, as the repository does when it saves.
 */
fun SharedSnapshot.splitMembersFor(group: SharedGroup, mode: SplitMode, expenseId: String?): List<SplitMember> =
    group.membersToSplit(mode, sharers = expenseId?.let { latestVersionOf(group.id, it) }?.shares.orEmpty().mapTo(mutableSetOf()) { it.memberId })

/** #185: [memberId]'s part when [amountMinor] of the group's money is split equally between everyone still in it. */
fun SharedGroup.equalPartOf(amountMinor: Long, memberId: String?): Satang {
    val members = membersToSplit()
    val check = split(amountMinor, SplitMode.Equally, members, emptyMap())
    return shares(check, Satang.ofMinorUnits(amountMinor), members, emptyMap(), SplitMode.Equally)
        .firstOrNull { it.memberId == memberId }?.satang ?: Satang.Zero
}
