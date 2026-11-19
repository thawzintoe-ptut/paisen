package com.ptut.expense.sync

import com.ptut.expense.data.local.entity.ExpenseShareEntity
import com.ptut.expense.data.local.entity.ExpenseVersionEntity
import com.ptut.expense.data.local.entity.PaymentRemovalEntity
import com.ptut.expense.data.local.entity.RepeatRuleEntity
import com.ptut.expense.data.local.entity.SharedPaymentEntity
import com.ptut.expense.data.repository.END_NEVER
import com.ptut.expense.data.repository.KIND_PAYMENT
import com.ptut.expense.data.repository.KIND_PAYMENT_REMOVAL
import com.ptut.expense.data.repository.KIND_VERSION
import com.ptut.expense.data.repository.toDomain
import com.ptut.expense.data.repository.toEntity
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.PaymentRemoval
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedPayment
import com.ptut.expense.domain.shared.sumsTo

/** The field the server stamps on every entry and repeat with its commit time. */
internal const val SYNCED_AT = "syncedAt"

/** One pull's log entries, written in one transaction. */
internal class LogBatch {
    val versions = mutableListOf<Pair<ExpenseVersionEntity, List<ExpenseShareEntity>>>()
    val payments = mutableListOf<SharedPaymentEntity>()
    val removals = mutableListOf<PaymentRemovalEntity>()

    fun isNotEmpty(): Boolean = versions.isNotEmpty() || payments.isNotEmpty() || removals.isNotEmpty()
}

/** Adds [doc] from [groupId]'s log when this build can read it and its money adds up; skips it otherwise. */
internal fun LogBatch.add(groupId: String, doc: FirestoreDoc, amountKey: String) {
    val f = doc.fields
    when (f.string("kind")) {
        KIND_VERSION -> {
            val version = ExpenseVersionEntity(
                versionId = doc.id,
                expenseId = f.string("expenseId") ?: return,
                groupId = groupId,
                version = f.int("version") ?: return,
                description = f.string("description").orEmpty(),
                occurredAtEpochMillis = f.long("occurredAt") ?: return,
                categoryId = f.string("categoryId"),
                subCategoryId = f.string("subCategoryId"),
                note = f.string("note"),
                currency = f.string("currency") ?: return,
                amountMinor = f.long("amountMinor") ?: return,
                rateUnscaled = f.long("rateUnscaled"),
                rateScale = f.int("rateScale"),
                amountSatang = f.long(amountKey) ?: return,
                payerMemberId = f.string("payerMemberId") ?: return,
                splitMode = f.string("splitMode") ?: return,
                authorMemberId = f.string("authorMemberId"),
                createdAtEpochMillis = f.long("createdAt") ?: return,
                removed = f.bool("removed"),
                cause = f.string("cause"),
            )
            val shares = f.list("shares").mapNotNull { share ->
                val s = share.asFields()
                ExpenseShareEntity(
                    versionId = doc.id,
                    memberId = s.string("memberId") ?: return@mapNotNull null,
                    weight = s.long("weight") ?: 0L,
                    amountMinor = s.long("amountMinor") ?: 0L,
                    amountSatang = s.long(amountKey) ?: 0L,
                )
            }
            // A split mode or rate this build cannot read is not taken: stored, it would stop every launch.
            // Nor is a split whose parts don't add up to the total: the balances would gain money from it.
            if (version.toDomain(shares) != null && addsUp(version, shares)) versions += version to shares
        }

        KIND_PAYMENT -> {
            val payment = SharedPaymentEntity(
                id = doc.id,
                groupId = groupId,
                fromMemberId = f.string("fromMemberId") ?: return,
                toMemberId = f.string("toMemberId") ?: return,
                amountSatang = f.long(amountKey) ?: return,
                method = f.string("method") ?: return,
                occurredAtEpochMillis = f.long("occurredAt") ?: return,
                authorMemberId = f.string("authorMemberId"),
                createdAtEpochMillis = f.long("createdAt") ?: return,
            )
            val real = payment.amountSatang in 1..MAX_MINOR_UNITS && payment.fromMemberId != payment.toMemberId
            if (real && payment.toDomain() != null) payments += payment
        }

        KIND_PAYMENT_REMOVAL -> removals += PaymentRemovalEntity(
            id = doc.id,
            groupId = groupId,
            paymentId = f.string("paymentId") ?: return,
            removed = f.bool("removed"),
            authorMemberId = f.string("authorMemberId"),
            createdAtEpochMillis = f.long("createdAt") ?: return,
        )
    }
}

/**
 * A repeat another member set up or changed, as Room would hold it, or null when a field it needs
 * is missing. This phone's progress and reminder come from [local].
 */
internal fun repeatEntity(groupId: String, doc: FirestoreDoc, local: RepeatRuleEntity?, nowMillis: Long): RepeatRuleEntity? {
    val f = doc.fields
    return RepeatRuleEntity(
        id = doc.id,
        groupId = groupId,
        kind = f.string("kind") ?: return null,
        frequency = f.string("frequency") ?: return null,
        startDate = f.string("start") ?: return null,
        endKind = f.string("endKind") ?: END_NEVER,
        endDate = f.string("endDate"),
        endTimes = f.int("endTimes"),
        remind = local?.remind ?: true,
        active = f.bool("active"),
        lastAdded = local?.lastAdded,
        description = f.string("description").orEmpty(),
        categoryId = f.string("categoryId"),
        currency = f.string("currency") ?: return null,
        amountMinor = f.long("amountMinor") ?: return null,
        rateUnscaled = f.long("rateUnscaled"),
        rateScale = f.int("rateScale"),
        payerMemberId = f.string("payerMemberId") ?: return null,
        splitMode = f.string("splitMode") ?: return null,
        weights = f.string("weights").orEmpty(),
        createdAtEpochMillis = f.long("createdAt") ?: nowMillis,
    )
}

/** The members an entry names: payer, author, both sides of a payment, and everyone in its split. */
internal fun FirestoreDoc.memberIds(): List<String> =
    listOfNotNull(fields.string("payerMemberId"), fields.string("authorMemberId"), fields.string("fromMemberId"), fields.string("toMemberId")) +
        fields.list("shares").mapNotNull { it.asFields().string("memberId") }

/**
 * Every phone stores a split whose parts add up to its total, in its currency and in satang (#134).
 * The rules set no ceiling, so one is set here: parts near 2^62 would wrap round to a small total.
 */
internal fun addsUp(version: ExpenseVersionEntity, shares: List<ExpenseShareEntity>): Boolean =
    shares.isNotEmpty() &&
        version.amountMinor in 0..MAX_MINOR_UNITS &&
        version.amountSatang in 0..MAX_MINOR_UNITS &&
        shares.map { it.amountMinor }.sumsTo(version.amountMinor) &&
        shares.map { it.amountSatang }.sumsTo(version.amountSatang)

/** The most one received amount may be, in minor units: a hundred billion baht, far past any real expense. */
internal const val MAX_MINOR_UNITS = 10_000_000_000_000L

/** A group type a newer app added is stored as Other, which every build can read. */
internal fun knownGroupType(name: String): String = GroupType.entries.firstOrNull { it.name == name }?.name ?: GroupType.Other.name

internal fun SharedGroup.fields(): Fields = mapOf(
    "name" to name,
    "type" to type.name,
    "icon" to icon,
    "simplifyDebts" to simplifyDebts,
    "createdAt" to createdAtEpochMillis,
)

/**
 * #48: a baht group keeps `amountSatang`, which every build reads. Any other group writes
 * `amountGroup` instead, so a build from before #48 skips the entry rather than read kyat as baht.
 * The rules hold the same line, so an older build cannot write baht into a kyat group either.
 */
internal object PullMark {
    /**
     * The mark a pull starts from. A phone that held the group in other money (a build from before
     * #48, which skipped every amountGroup entry but still moved its mark) pulls the whole log once,
     * or those entries never come.
     */
    fun kept(localCurrency: String?, localMark: Long?, serverCurrency: String): Long? = localMark?.takeIf { localCurrency == serverCurrency }
}

internal object WireAmount {
    fun key(groupCurrency: String): String = if (groupCurrency == Currencies.Thb.code) "amountSatang" else "amountGroup"
}

internal fun SharedExpense.fields(uid: String, groupCurrency: String): Fields = mapOf(
    "kind" to KIND_VERSION,
    "authorUid" to uid,
    "createdAt" to createdAtEpochMillis,
    "expenseId" to id,
    "version" to version.toLong(),
    "description" to description,
    "occurredAt" to occurredAtEpochMillis,
    "categoryId" to categoryId,
    "subCategoryId" to subCategoryId,
    "note" to note,
    "currency" to currency,
    "amountMinor" to amountMinor,
    "rateUnscaled" to rate?.unscaled,
    "rateScale" to rate?.scale?.toLong(),
    WireAmount.key(groupCurrency) to satang.toMinorUnits(),
    "payerMemberId" to payerMemberId,
    "splitMode" to splitMode.name,
    "authorMemberId" to authorMemberId,
    "removed" to removed,
    "cause" to cause,
    "shares" to shares.map {
        mapOf("memberId" to it.memberId, "weight" to it.weight, "amountMinor" to it.amountMinor, WireAmount.key(groupCurrency) to it.satang.toMinorUnits())
    },
)

internal fun SharedPayment.fields(uid: String, groupCurrency: String): Fields = mapOf(
    "kind" to KIND_PAYMENT,
    "authorUid" to uid,
    "createdAt" to createdAtEpochMillis,
    "fromMemberId" to fromMemberId,
    "toMemberId" to toMemberId,
    WireAmount.key(groupCurrency) to amount.toMinorUnits(),
    "method" to method.name,
    "occurredAt" to occurredAtEpochMillis,
    "authorMemberId" to authorMemberId,
)

internal fun PaymentRemoval.fields(uid: String): Fields = mapOf(
    "kind" to KIND_PAYMENT_REMOVAL,
    "authorUid" to uid,
    "createdAt" to createdAtEpochMillis,
    "paymentId" to paymentId,
    "removed" to removed,
    "authorMemberId" to authorMemberId,
)

/** #182's template as Firestore holds it. Never this phone's progress or its reminder choice. */
internal fun RepeatRule.fields(uid: String): Fields = toEntity().let { rule ->
    mapOf(
        "authorUid" to uid,
        "createdAt" to rule.createdAtEpochMillis,
        "kind" to rule.kind,
        "frequency" to rule.frequency,
        "start" to rule.startDate,
        "endKind" to rule.endKind,
        "endDate" to rule.endDate,
        "endTimes" to rule.endTimes?.toLong(),
        "active" to rule.active,
        "description" to rule.description,
        "categoryId" to rule.categoryId,
        "currency" to rule.currency,
        "amountMinor" to rule.amountMinor,
        "rateUnscaled" to rule.rateUnscaled,
        "rateScale" to rule.rateScale?.toLong(),
        "payerMemberId" to rule.payerMemberId,
        "splitMode" to rule.splitMode,
        "weights" to rule.weights,
    )
}
