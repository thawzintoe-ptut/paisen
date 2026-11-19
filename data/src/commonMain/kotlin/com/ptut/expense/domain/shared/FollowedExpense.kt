package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.TransactionEdit

/** #186: the next version of a shared expense after your own transaction changed in History. */
data class FollowedExpense(
    val amountMinor: Long,
    val satang: Satang,
    val occurredAtEpochMillis: Long,
    val categoryId: String?,
    val subCategoryId: String?,
    val note: String?,
    val description: String,
    val splitMode: SplitMode,
    val shares: List<Share>,
)

/**
 * Null when nothing moved. Each field is compared with [before], never with this version: a field
 * left alone in History keeps whatever another phone made it since.
 */
fun SharedExpense.followed(
    before: TransactionEdit,
    after: TransactionEdit,
    titleChange: TitleChange?,
    groupMembers: List<SplitMember>,
    groupCurrency: String,
    homeCurrency: String,
): FollowedExpense? {
    // A foreign-currency expense keeps its own amount: only one in both the group's money and the
    // ledger's home can take History's figure (#48).
    val inHome = currency == groupCurrency && currency == homeCurrency
    val amountMoved = inHome && after.amount != before.amount
    val dateMoved = before.occurredAtEpochMillis != null && after.occurredAtEpochMillis != before.occurredAtEpochMillis
    val categoryMoved = after.categoryId != before.categoryId || after.subCategoryId != before.subCategoryId
    val newAmount = if (amountMoved) after.amount.toMinorUnits() else amountMinor
    val occurredAt = if (dateMoved) after.occurredAtEpochMillis ?: occurredAtEpochMillis else occurredAtEpochMillis
    val newCategory = if (categoryMoved) after.categoryId else categoryId
    val newSubCategory = if (categoryMoved) after.subCategoryId else subCategoryId
    val memo = after.memo.cleanMemo()
    val newNote = if (memo != before.memo.cleanMemo()) memo else note
    val newDescription = if (categoryMoved) ExpenseTitle.followed(description, titleChange) else description
    if (newAmount == amountMinor && occurredAt == occurredAtEpochMillis && newCategory == categoryId &&
        newSubCategory == subCategoryId && newNote == note && newDescription == description
    ) {
        return null
    }
    val newSatang = if (amountMoved) after.amount else satang
    val split = followedSplit(newAmount, groupMembers)
    return FollowedExpense(
        amountMinor = newAmount,
        satang = newSatang,
        occurredAtEpochMillis = occurredAt,
        categoryId = newCategory,
        subCategoryId = newSubCategory,
        note = newNote,
        description = newDescription,
        splitMode = split.mode,
        shares = shares(split.check, newSatang, split.members, split.weights, split.mode),
    )
}

private class FollowedSplit(val mode: SplitMode, val members: List<SplitMember>, val weights: Map<String, Long>, val check: SplitCheck)

/**
 * The same people split the same way at [newAmount]. O1 can push the payer below zero when the
 * amount falls; then every part shrinks in step, as Shares weighted by the old parts.
 */
private fun SharedExpense.followedSplit(newAmount: Long, groupMembers: List<SplitMember>): FollowedSplit {
    val members = groupMembers.filter { member -> shares.any { it.memberId == member.id } }
    val weights = when (splitMode) {
        SplitMode.Equally -> shares.associate { it.memberId to 1L }
        SplitMode.Exact -> shares.associate { it.memberId to it.amountMinor }.rebalancedTo(newAmount, payerMemberId)
        else -> shares.associate { it.memberId to it.weight }
    }
    val exact = split(newAmount, splitMode, members, weights)
    if (exact.complete) return FollowedSplit(splitMode, members, weights, exact)
    val oldParts = shares.associate { it.memberId to it.amountMinor }
    return FollowedSplit(SplitMode.Shares, members, oldParts, split(newAmount, SplitMode.Shares, members, oldParts))
}

private fun String?.cleanMemo(): String? = this?.trim()?.ifEmpty { null }
