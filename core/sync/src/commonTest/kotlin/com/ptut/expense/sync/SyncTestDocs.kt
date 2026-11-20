package com.ptut.expense.sync

import com.ptut.expense.data.local.entity.GroupMemberEntity
import com.ptut.expense.data.local.entity.SharedGroupEntity
import com.ptut.expense.data.local.entity.SharedPaymentEntity
import com.ptut.expense.data.local.entity.SyncOutboxEntity
import com.ptut.expense.data.repository.KIND_PAYMENT
import com.ptut.expense.domain.shared.GroupType

internal const val UID = "uid-1"

/** This phone's name in g1; [holdGroups] gives each group its own. */
internal const val ME = "me-g1"
internal const val MARK = 500_000L
internal const val TEN_MINUTES = 600_000L

/** Remote groups already on this phone, pulled before, each with this phone's name in it. */
internal fun holdGroups(dao: FakeSharedDao, firestore: FakeFirestore, vararg ids: String) {
    ids.forEach { id ->
        dao.groups[id] = SharedGroupEntity(
            id = id,
            name = "Flat",
            type = GroupType.Other.name,
            icon = "group",
            simplifyDebts = true,
            createdAtEpochMillis = 1,
            meMemberId = "me-$id",
            remote = true,
            lastSyncedAtEpochMillis = MARK,
        )
        dao.members["me-$id"] = GroupMemberEntity("me-$id", id, "Mali", personId = null, archived = false, createdAtEpochMillis = 1, claimedByUid = UID)
        firestore.docs["groups/$id"] = groupDoc(id)
        firestore.collections["groups/$id/members"] = listOf(memberDoc("me-$id"))
    }
}

internal fun queuePayment(dao: FakeSharedDao, id: String, groupId: String) {
    dao.payments[id] = SharedPaymentEntity(
        id = id,
        groupId = groupId,
        fromMemberId = "me-$groupId",
        toMemberId = "bo",
        amountSatang = 5_000,
        method = "Cash",
        occurredAtEpochMillis = 2,
        authorMemberId = "me-$groupId",
        createdAtEpochMillis = 2,
    )
    dao.outboxRows.value += SyncOutboxEntity("$KIND_PAYMENT:$id", groupId, KIND_PAYMENT, id, 1)
}

internal fun groupDoc(id: String, name: String = "Flat", deleted: Boolean = false) = FirestoreDoc(
    id = id,
    name = "groups/$id",
    fields = mapOf(
        "name" to name,
        "type" to GroupType.Other.name,
        "icon" to "group",
        "simplifyDebts" to true,
        "createdAt" to 1L,
        "currency" to "THB",
        "memberUids" to listOf(UID),
        "deleted" to deleted,
    ),
)

internal fun memberDoc(id: String) = FirestoreDoc(
    id = id,
    name = id,
    fields = mapOf("name" to id, "archived" to false, "createdAt" to 1L, "claimedByUid" to if (id.startsWith("me-")) UID else null),
)

/** A PromptPay ID in the form the app saves, split so no scanner reads it as a real phone number. */
internal const val PROMPT_PAY = "08" + "12345678"

/** [memberId]'s PromptPay ID in the members-only place, stored by [holder]. */
internal fun payDoc(memberId: String, holder: String, promptPay: String = PROMPT_PAY) = FirestoreDoc(
    id = memberId,
    name = "groups/g1/pay/$memberId",
    fields = mapOf("promptPay" to promptPay, "claimedByUid" to holder),
)

/** A name held by [holder], with [promptPay] on the name itself, as builds before M5 store it. */
internal fun heldDoc(id: String, holder: String, promptPay: String? = null) = memberDoc(id).let {
    it.copy(fields = it.fields + ("claimedByUid" to holder) + ("promptPay" to promptPay))
}

internal fun paymentDoc(id: String, from: String, to: String) = FirestoreDoc(
    id = id,
    name = id,
    fields = mapOf(
        "kind" to "payment",
        "fromMemberId" to from,
        "toMemberId" to to,
        "amountSatang" to 5_000L,
        "method" to "Cash",
        "occurredAt" to 2L,
        "authorMemberId" to from,
        "createdAt" to 2L,
    ),
)

internal fun versionDoc(id: String, total: Long, parts: List<Long>) = FirestoreDoc(
    id = id,
    name = id,
    fields = mapOf(
        "kind" to "version",
        "expenseId" to "e-$id",
        "version" to 1L,
        "occurredAt" to 2L,
        "currency" to "THB",
        "amountMinor" to total,
        "amountSatang" to total,
        "payerMemberId" to ME,
        "splitMode" to "Exact",
        "authorMemberId" to ME,
        "createdAt" to 2L,
        "shares" to parts.mapIndexed { index, part ->
            mapOf("memberId" to if (index == 0) ME else "bo", "weight" to part, "amountMinor" to part, "amountSatang" to part)
        },
    ),
)

internal fun repeatDoc(id: String, vararg changed: Pair<String, Any?>) = FirestoreDoc(
    id = id,
    name = id,
    fields = mapOf(
        "kind" to "Rent",
        "frequency" to "Monthly",
        "start" to "2026-02-01",
        "active" to true,
        "currency" to "THB",
        "amountMinor" to 1_200_000L,
        "payerMemberId" to ME,
        "splitMode" to "Equally",
        "weights" to "",
        "createdAt" to 2L,
    ) + changed,
)
