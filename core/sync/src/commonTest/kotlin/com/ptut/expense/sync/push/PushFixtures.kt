package com.ptut.expense.sync.push

import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.Share
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedPayment
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.SplitMode

internal val mali = Member("m-mali", "g1", "Mali", personId = null, claimedByUid = "uid-mali")
internal val cara = Member("m-cara", "g1", "Cara", personId = null, claimedByUid = "uid-cara")

internal fun group(vararg members: Member) = SharedGroup(
    id = "g1",
    name = "Trip",
    type = GroupType.Trip,
    icon = "trip",
    simplifyDebts = true,
    createdAtEpochMillis = 1,
    meMemberId = cara.id,
    members = members.toList(),
    remote = true,
)

internal fun version(versionId: String, description: String = "Rice", version: Int = 1, removed: Boolean = false, cause: String? = null) = SharedExpense(
    id = "e1",
    versionId = versionId,
    version = version,
    groupId = "g1",
    description = description,
    occurredAtEpochMillis = 2,
    categoryId = null,
    subCategoryId = null,
    note = null,
    currency = "THB",
    amountMinor = 32_000,
    rate = null,
    satang = Satang(32_000),
    payerMemberId = mali.id,
    splitMode = SplitMode.Equally,
    shares = listOf(Share(mali.id, 1, 16_000, Satang(16_000)), Share(cara.id, 1, 16_000, Satang(16_000))),
    authorMemberId = mali.id,
    createdAtEpochMillis = version.toLong(),
    removed = removed,
    cause = cause,
)

internal val payment = SharedPayment("p1", "g1", mali.id, cara.id, Satang(5_000), PaymentMethod.Cash, 2, mali.id, 3)

internal fun snapshot(group: SharedGroup, versions: List<SharedExpense> = emptyList(), payments: List<SharedPayment> = emptyList()) =
    SharedSnapshot(listOf(group), emptyList(), versions, payments, emptyMap())

/** Kept in memory; [canRetire] says whether the platform could retire an installation. */
internal class MemoryPushLeftovers(private val canRetire: Boolean = false) : PushLeftovers {
    private var kept: PushLeftover? = null
    val retired = mutableListOf<String>()

    override fun read(): PushLeftover? = kept

    override fun write(leftover: PushLeftover?) {
        kept = leftover
    }

    override suspend fun retire(token: String): Boolean {
        if (canRetire) retired += token
        return canRetire
    }
}
