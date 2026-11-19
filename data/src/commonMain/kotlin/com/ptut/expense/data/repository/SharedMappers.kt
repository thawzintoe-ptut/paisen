package com.ptut.expense.data.repository

import com.ptut.expense.data.local.entity.ExpenseShareEntity
import com.ptut.expense.data.local.entity.ExpenseVersionEntity
import com.ptut.expense.data.local.entity.GroupMemberEntity
import com.ptut.expense.data.local.entity.PaymentRemovalEntity
import com.ptut.expense.data.local.entity.RepeatRuleEntity
import com.ptut.expense.data.local.entity.SharedGroupEntity
import com.ptut.expense.data.local.entity.SharedPaymentEntity
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.PaymentRemoval
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.RepeatEnd
import com.ptut.expense.domain.shared.RepeatFrequency
import com.ptut.expense.domain.shared.RepeatKind
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.Share
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedPayment
import com.ptut.expense.domain.shared.SplitMode
import com.ptut.expense.domain.shared.readableRate
import com.ptut.expense.domain.shared.toLocalDateOrNull

/** What an outbox row carries (#189). */
const val KIND_GROUP = "group"
const val KIND_MEMBER = "member"
const val KIND_VERSION = "version"
const val KIND_PAYMENT = "payment"
const val KIND_REPEAT = "repeat"
const val KIND_PAYMENT_REMOVAL = "paymentRemoval"

fun SharedGroupEntity.toDomain(members: List<GroupMemberEntity>) = SharedGroup(
    id = id,
    name = name,
    // A type a newer app added reads as Other here rather than failing the whole snapshot.
    type = GroupType.entries.firstOrNull { it.name == type } ?: GroupType.Other,
    icon = icon,
    simplifyDebts = simplifyDebts,
    createdAtEpochMillis = createdAtEpochMillis,
    meMemberId = meMemberId,
    members = members.map { Member(it.id, it.groupId, it.name, it.personId, it.archived, it.claimedByUid, it.promptPayId) },
    remote = remote,
    creatorUid = creatorUid,
    inviteCode = inviteCode,
    inviteActive = inviteActive,
    lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
    lastSeenAtEpochMillis = lastSeenAtEpochMillis,
    currency = currency,
)

/**
 * Null for a version this build cannot read: a split mode it does not know, or a rate [Rate] refuses.
 * Sync takes no such version, and one already stored is left out rather than crash every launch.
 */
fun ExpenseVersionEntity.toDomain(shares: List<ExpenseShareEntity>): SharedExpense? {
    val mode = SplitMode.entries.firstOrNull { it.name == splitMode } ?: return null
    if (!readableRate(rateUnscaled, rateScale)) return null
    return SharedExpense(
        id = expenseId,
        versionId = versionId,
        version = version,
        groupId = groupId,
        description = description,
        occurredAtEpochMillis = occurredAtEpochMillis,
        categoryId = categoryId,
        subCategoryId = subCategoryId,
        note = note,
        currency = currency,
        amountMinor = amountMinor,
        rate = if (rateUnscaled != null && rateScale != null) Rate(rateUnscaled, rateScale) else null,
        satang = Satang.ofMinorUnits(amountSatang),
        payerMemberId = payerMemberId,
        splitMode = mode,
        shares = shares.map { Share(it.memberId, it.weight, it.amountMinor, Satang.ofMinorUnits(it.amountSatang)) },
        authorMemberId = authorMemberId,
        createdAtEpochMillis = createdAtEpochMillis,
        removed = removed,
        cause = cause,
    )
}

fun PaymentRemovalEntity.toDomain() = PaymentRemoval(
    id = id,
    groupId = groupId,
    paymentId = paymentId,
    removed = removed,
    authorMemberId = authorMemberId,
    createdAtEpochMillis = createdAtEpochMillis,
)

/** Null for a method this build does not know, so sync takes no such payment and a stored one is left out. */
fun SharedPaymentEntity.toDomain(): SharedPayment? {
    val known = PaymentMethod.entries.firstOrNull { it.name == method } ?: return null
    return SharedPayment(
        id = id,
        groupId = groupId,
        fromMemberId = fromMemberId,
        toMemberId = toMemberId,
        amount = Satang.ofMinorUnits(amountSatang),
        method = known,
        occurredAtEpochMillis = occurredAtEpochMillis,
        authorMemberId = authorMemberId,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

/** Null for a kind, a date, a split or a rate this build cannot read: a stored one is left out, and sync takes none. */
fun RepeatRuleEntity.toDomain(): RepeatRule? {
    if (!readableRate(rateUnscaled, rateScale)) return null
    return RepeatRule(
        id = id,
        groupId = groupId,
        kind = RepeatKind.entries.firstOrNull { it.name == kind } ?: return null,
        frequency = RepeatFrequency.entries.firstOrNull { it.name == frequency } ?: return null,
        start = startDate.toLocalDateOrNull() ?: return null,
        end = when (endKind) {
            END_ON_DATE -> endDate?.let { RepeatEnd.OnDate(it.toLocalDateOrNull() ?: return null) } ?: RepeatEnd.Never
            END_AFTER -> endTimes?.let { RepeatEnd.After(it) } ?: RepeatEnd.Never
            else -> RepeatEnd.Never
        },
        remind = remind,
        active = active,
        lastAdded = lastAdded?.let { it.toLocalDateOrNull() ?: return null },
        description = description,
        categoryId = categoryId,
        currency = currency,
        amountMinor = amountMinor,
        rate = if (rateUnscaled != null && rateScale != null) Rate(rateUnscaled, rateScale) else null,
        payerMemberId = payerMemberId,
        splitMode = SplitMode.entries.firstOrNull { it.name == splitMode } ?: return null,
        weights = weights.split(';').filter { '=' in it }.associate { it.substringBefore('=') to (it.substringAfter('=').toLongOrNull() ?: return null) },
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

fun RepeatRule.toEntity() = RepeatRuleEntity(
    id = id,
    groupId = groupId,
    kind = kind.name,
    frequency = frequency.name,
    startDate = start.toString(),
    endKind = when (end) {
        RepeatEnd.Never -> END_NEVER
        is RepeatEnd.OnDate -> END_ON_DATE
        is RepeatEnd.After -> END_AFTER
    },
    endDate = (end as? RepeatEnd.OnDate)?.last?.toString(),
    endTimes = (end as? RepeatEnd.After)?.times,
    remind = remind,
    active = active,
    lastAdded = lastAdded?.toString(),
    description = description,
    categoryId = categoryId,
    currency = currency,
    amountMinor = amountMinor,
    rateUnscaled = rate?.unscaled,
    rateScale = rate?.scale,
    payerMemberId = payerMemberId,
    splitMode = splitMode.name,
    weights = weights.entries.joinToString(";") { "${it.key}=${it.value}" },
    createdAtEpochMillis = createdAtEpochMillis,
)

const val END_NEVER = "Never"
internal const val END_ON_DATE = "OnDate"
internal const val END_AFTER = "After"
