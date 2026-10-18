package com.ptut.expense.data.repository

import com.ptut.expense.data.local.dao.SharedDao
import com.ptut.expense.data.local.dao.TransactionDao
import com.ptut.expense.data.local.dao.withCheckedSubCategory
import com.ptut.expense.data.local.entity.CurrencyRateEntity
import com.ptut.expense.data.local.entity.ExpenseLinkEntity
import com.ptut.expense.data.local.entity.ExpenseShareEntity
import com.ptut.expense.data.local.entity.ExpenseVersionEntity
import com.ptut.expense.data.local.entity.GroupMemberEntity
import com.ptut.expense.data.local.entity.PaymentRemovalEntity
import com.ptut.expense.data.local.entity.PersonEntity
import com.ptut.expense.data.local.entity.RepeatRuleEntity
import com.ptut.expense.data.local.entity.SharedGroupEntity
import com.ptut.expense.data.local.entity.SharedPaymentEntity
import com.ptut.expense.data.local.entity.SyncOutboxEntity
import com.ptut.expense.data.local.entity.toEntity
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.ExpenseCause
import com.ptut.expense.domain.shared.ExpenseChanged
import com.ptut.expense.domain.shared.ExpenseDraft
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.PaymentDraft
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.PaymentRemoval
import com.ptut.expense.domain.shared.Person
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.RepeatEnd
import com.ptut.expense.domain.shared.RepeatFrequency
import com.ptut.expense.domain.shared.RepeatKind
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.Share
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedPayment
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.SplitMode
import com.ptut.expense.domain.shared.TransactionSeed
import com.ptut.expense.domain.shared.due
import com.ptut.expense.domain.shared.entryId
import com.ptut.expense.domain.shared.membersToSplit
import com.ptut.expense.domain.shared.rebalancedTo
import com.ptut.expense.domain.shared.sharedExpenseOf
import com.ptut.expense.domain.shared.shares
import com.ptut.expense.domain.shared.split
import com.ptut.expense.domain.shared.splitMembers
import com.ptut.expense.domain.shared.toSatang
import com.ptut.expense.domain.startOfDayEpochMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * [SharedRepository] over Room. The split is worked out here from the draft's weights, so what is
 * stored is always a split that adds up (#134) and never one a screen assembled by hand.
 */
@OptIn(ExperimentalUuidApi::class)
class RoomSharedRepository(
    private val dao: SharedDao,
    private val transactionDao: TransactionDao,
    private val ledger: LedgerRepository,
    scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val newId: () -> String = { Uuid.random().toString() },
) : SharedRepository {

    override val snapshot: StateFlow<SharedSnapshot> = combine(
        combine(dao.observeGroups(), dao.observeMembers(), dao.observePeople()) { groups, members, people ->
            val byGroup = members.groupBy { it.groupId }
            groups.map { it.toDomain(byGroup[it.id].orEmpty()) } to people.map { Person(it.id, it.name, it.archived) }
        },
        combine(dao.observeVersions(), dao.observeShares()) { versions, shares ->
            val byVersion = shares.groupBy { it.versionId }
            versions.mapNotNull { it.toDomain(byVersion[it.versionId].orEmpty()) }
        },
        combine(dao.observePayments(), dao.observePaymentRemovals(), ::Pair),
        dao.observeLinks(),
        dao.observeRepeats(),
    ) { (groups, people), versions, (payments, removals), links, repeats ->
        SharedSnapshot(
            groups = groups,
            people = people,
            expenseVersions = versions,
            payments = payments.mapNotNull { it.toDomain() },
            links = links.associate { it.expenseId to it.transactionId },
            repeats = repeats.map { it.toDomain() },
            paymentRemovals = removals.map { it.toDomain() },
        )
    }.stateIn(scope, SharingStarted.Eagerly, SharedSnapshot.Empty)

    override suspend fun createGroup(name: String, type: GroupType, icon: String, members: List<NewMember>): String {
        val now = clock.now().toEpochMilliseconds()
        val groupId = newId()
        val meId = newId()
        val (people, rows) = membersOf(groupId, members, now)
        dao.createGroup(
            group = SharedGroupEntity(groupId, name.trim(), type.name, icon, simplifyDebts = true, createdAtEpochMillis = now, meMemberId = meId),
            people = people,
            members = listOf(GroupMemberEntity(meId, groupId, YOU, personId = null, archived = false, createdAtEpochMillis = now)) + rows,
        )
        return groupId
    }

    override suspend fun addMembers(groupId: String, members: List<NewMember>) {
        val (people, rows) = membersOf(groupId, members, clock.now().toEpochMilliseconds())
        dao.addMembers(people, rows)
        rows.forEach { queueIfShared(groupId, KIND_MEMBER, it.id) }
    }

    override suspend fun setPromptPay(groupId: String, promptPayId: String?) {
        val me = dao.group(groupId)?.meMemberId ?: return
        dao.setPromptPay(me, promptPayId)
        queueIfShared(groupId, KIND_MEMBER, me)
    }

    override suspend fun editGroup(groupId: String, name: String, type: GroupType, icon: String) {
        dao.editGroup(groupId, name.trim(), type.name, icon)
        queueIfShared(groupId, KIND_GROUP, groupId)
    }

    override suspend fun deleteGroup(groupId: String) = dao.forgetGroup(groupId)

    override suspend fun setPersonArchived(personId: String, archived: Boolean) {
        dao.setPersonArchived(personId, archived)
        val members = dao.membersOfPerson(personId).filter { mayRemoveFrom(it.groupId) }
        dao.setMembersArchived(members.map { it.id }, archived)
        members.forEach { queueIfShared(it.groupId, KIND_MEMBER, it.id) }
    }

    override suspend fun removeMember(groupId: String, memberId: String) {
        if (dao.group(groupId)?.meMemberId == memberId || !mayRemoveFrom(groupId)) return
        dao.setMembersArchived(listOf(memberId), true)
        queueIfShared(groupId, KIND_MEMBER, memberId)
    }

    /** A group only this phone holds, or a synced one this phone's account created (#190's rule). */
    private suspend fun mayRemoveFrom(groupId: String): Boolean {
        val group = dao.group(groupId) ?: return false
        if (!group.remote) return true
        val me = dao.membersOf(groupId).firstOrNull { it.id == group.meMemberId }
        return group.creatorUid != null && me?.claimedByUid == group.creatorUid
    }

    /** #189: a change to a shared group waits in the outbox until it reaches Firestore. */
    private suspend fun queueIfShared(groupId: String, kind: String, rowId: String) {
        if (dao.group(groupId)?.remote != true) return
        dao.queue(listOf(SyncOutboxEntity("$kind:$rowId", groupId, kind, rowId, clock.now().toEpochMilliseconds())))
    }

    /** A new name becomes a person too (#56), so the People list knows them across groups. */
    private fun membersOf(groupId: String, members: List<NewMember>, now: Long): Pair<List<PersonEntity>, List<GroupMemberEntity>> {
        val people = mutableListOf<PersonEntity>()
        val rows = members.filter { it.name.isNotBlank() }.map { member ->
            val personId = member.personId ?: newId().also { people += PersonEntity(it, member.name.trim(), archived = false) }
            GroupMemberEntity(newId(), groupId, member.name.trim(), personId, archived = false, createdAtEpochMillis = now)
        }
        return people to rows
    }

    override suspend fun setSimplifyDebts(groupId: String, on: Boolean) {
        dao.setSimplifyDebts(groupId, on)
        queueIfShared(groupId, KIND_GROUP, groupId)
    }

    override suspend fun saveExpense(draft: ExpenseDraft): String {
        val group = requireNotNull(snapshot.value.group(draft.groupId)) { "No group ${draft.groupId}" }
        val now = clock.now().toEpochMilliseconds()
        val currency = Currencies.of(draft.currency)
        val satang = toSatang(draft.amountMinor, currency, draft.rate)
        val latest = draft.expenseId?.let { dao.latestVersion(it) }
        if (latest != null && draft.baseVersionId != null && latest.versionId != draft.baseVersionId) {
            throw ExpenseChanged(latest.versionId, latest.removed)
        }
        val sharers = latest?.let { dao.sharesOf(it.versionId) }.orEmpty()
        val members = group.membersToSplit(keeping = sharers.mapTo(mutableSetOf()) { it.memberId })
        val check = split(draft.amountMinor, draft.splitMode, members, draft.weights)
        require(check.complete) { "A split that does not add up is not saved" }

        val expenseId = draft.expenseId ?: newId()
        val versionId = newId()
        val version = (dao.latestVersionNumber(expenseId) ?: 0) + 1
        val shares = shares(check, satang, members, draft.weights, draft.splitMode)

        val row = ExpenseVersionEntity(
            versionId = versionId,
            expenseId = expenseId,
            groupId = draft.groupId,
            version = version,
            description = draft.description.trim(),
            occurredAtEpochMillis = draft.occurredAtEpochMillis,
            categoryId = draft.categoryId,
            subCategoryId = draft.subCategoryId,
            note = draft.note?.trim()?.ifEmpty { null },
            currency = currency.code,
            amountMinor = draft.amountMinor,
            rateUnscaled = draft.rate?.unscaled,
            rateScale = draft.rate?.scale,
            amountSatang = satang.toMinorUnits(),
            payerMemberId = draft.payerMemberId,
            splitMode = draft.splitMode.name,
            authorMemberId = group.meMemberId,
            createdAtEpochMillis = now,
            removed = false,
        )
        val link = dao.link(expenseId)
        val youPaid = group.isMe(draft.payerMemberId)
        val shareOf = draft.linkTransactionId?.takeIf { link == null }
        // #186: a transaction is shared once at most. A link left by a removed expense gives way.
        if (shareOf != null) {
            require(snapshot.value.sharedExpenseOf(shareOf) == null) { "Transaction $shareOf is already shared" }
            dao.unlinkTransaction(shareOf)
        }
        dao.writeVersion(
            version = row,
            shares = shares.map { ExpenseShareEntity(versionId, it.memberId, it.weight, it.amountMinor, it.satang.toMinorUnits()) },
            newTransaction = if (youPaid && link == null && shareOf == null && draft.categoryId != null) yourTransaction(draft, satang, now) else null,
        )
        if (shareOf != null) dao.upsertLink(ExpenseLinkEntity(expenseId = expenseId, transactionId = shareOf))
        // #32: the full amount you paid stays your own transaction's amount after an edit too.
        if (youPaid && link != null && draft.categoryId != null) {
            ledger.applyEdit(
                link.transactionId,
                TransactionEdit(
                    amount = satang,
                    categoryId = draft.categoryId,
                    memo = draft.note?.trim()?.ifEmpty { null },
                    subCategoryId = draft.subCategoryId,
                    occurredAtEpochMillis = draft.occurredAtEpochMillis,
                ),
            )
        }
        if (draft.rate != null) dao.upsertRate(CurrencyRateEntity(currency.code, draft.rate.unscaled, draft.rate.scale, now))
        queueIfShared(draft.groupId, KIND_VERSION, versionId)
        return expenseId
    }

    private suspend fun yourTransaction(draft: ExpenseDraft, satang: Satang, now: Long) = transactionDao.withCheckedSubCategory(
        TransactionDraft(
            amount = satang,
            direction = Direction.Expense,
            categoryId = requireNotNull(draft.categoryId),
            subCategoryId = draft.subCategoryId,
            counterparty = draft.description.trim().ifEmpty { null },
            occurredAtEpochMillis = draft.occurredAtEpochMillis,
        ).toEntity(id = newId(), occurredAtEpochMillis = draft.occurredAtEpochMillis, filedAt = now)
            .copy(memo = draft.note?.trim()?.ifEmpty { null }),
    )

    override suspend fun removeExpense(expenseId: String) {
        val last = requireNotNull(dao.latestVersion(expenseId)) { "No expense $expenseId" }
        if (last.removed) return
        val now = clock.now().toEpochMilliseconds()
        val versionId = newId()
        val group = snapshot.value.group(last.groupId)
        dao.writeVersion(
            version = last.copy(
                versionId = versionId,
                version = last.version + 1,
                authorMemberId = group?.meMemberId,
                createdAtEpochMillis = now,
                removed = true,
            ),
            shares = dao.sharesOf(last.versionId).map { it.copy(versionId = versionId) },
            newTransaction = null,
        )
        queueIfShared(last.groupId, KIND_VERSION, versionId)
    }

    override suspend fun recordPayment(draft: PaymentDraft): String {
        val id = newId()
        val now = clock.now().toEpochMilliseconds()
        dao.insertPayment(
            SharedPaymentEntity(
                id = id,
                groupId = draft.groupId,
                fromMemberId = draft.fromMemberId,
                toMemberId = draft.toMemberId,
                amountSatang = draft.amount.toMinorUnits(),
                method = draft.method.name,
                occurredAtEpochMillis = draft.occurredAtEpochMillis,
                authorMemberId = snapshot.value.group(draft.groupId)?.meMemberId,
                createdAtEpochMillis = now,
            ),
        )
        queueIfShared(draft.groupId, KIND_PAYMENT, id)
        return id
    }

    override suspend fun transactionSeed(transactionId: String): TransactionSeed? {
        val row = dao.transaction(transactionId) ?: return null
        if (row.direction != Direction.Expense.name) return null
        return TransactionSeed(
            transactionId = row.id,
            amount = Satang.ofMinorUnits(row.amountMinor),
            occurredAtEpochMillis = row.occurredAtEpochMillis,
            description = row.counterparty.orEmpty(),
            categoryId = row.categoryId,
            subCategoryId = row.subCategoryId,
        )
    }

    override suspend fun followTransaction(transactionId: String) {
        val expenseId = snapshot.value.links.entries.firstOrNull { it.value == transactionId }?.key ?: return
        val last = dao.latestVersion(expenseId)?.takeUnless { it.removed } ?: return
        val row = dao.transaction(transactionId) ?: return
        val group = snapshot.value.group(last.groupId) ?: return
        // A foreign-currency expense keeps its own amount: only a baht one can take History's figure.
        val baht = last.currency == Currencies.Thb.code
        val amountMinor = if (baht) row.amountMinor else last.amountMinor
        if (amountMinor == last.amountMinor && row.occurredAtEpochMillis == last.occurredAtEpochMillis &&
            row.categoryId == last.categoryId && row.subCategoryId == last.subCategoryId
        ) {
            return
        }
        val old = dao.sharesOf(last.versionId)
        val mode = SplitMode.valueOf(last.splitMode)
        val members = group.splitMembers().filter { member -> old.any { it.memberId == member.id } }
        val weights = when (mode) {
            SplitMode.Equally -> old.associate { it.memberId to 1L }
            SplitMode.Exact -> old.associate { it.memberId to it.amountMinor }.rebalancedTo(amountMinor, last.payerMemberId)
            else -> old.associate { it.memberId to it.weight }
        }
        val exact = split(amountMinor, mode, members, weights)
        // O1 can push the payer below zero when the amount falls; then every part shrinks in step.
        val check = if (exact.complete) exact else split(amountMinor, SplitMode.Shares, members, old.associate { it.memberId to it.amountMinor })
        val satang = if (baht) Satang.ofMinorUnits(row.amountMinor) else Satang.ofMinorUnits(last.amountSatang)
        val now = clock.now().toEpochMilliseconds()
        val versionId = newId()
        val written = shares(check, satang, members, weights, if (exact.complete) mode else SplitMode.Shares)
        dao.writeVersion(
            version = last.copy(
                versionId = versionId,
                version = last.version + 1,
                occurredAtEpochMillis = row.occurredAtEpochMillis,
                categoryId = row.categoryId,
                subCategoryId = row.subCategoryId,
                amountMinor = amountMinor,
                amountSatang = satang.toMinorUnits(),
                splitMode = if (exact.complete) mode.name else SplitMode.Shares.name,
                authorMemberId = group.meMemberId,
                createdAtEpochMillis = now,
                cause = ExpenseCause.TRANSACTION,
            ),
            shares = written.map { ExpenseShareEntity(versionId, it.memberId, it.weight, it.amountMinor, it.satang.toMinorUnits()) },
            newTransaction = null,
        )
        queueIfShared(last.groupId, KIND_VERSION, versionId)
    }

    /** Payments removed from a group only on this phone, held so Undo can put the same row back. */
    private val removedHere = mutableMapOf<String, SharedPaymentEntity>()

    override suspend fun removePayment(paymentId: String) {
        val payment = dao.payment(paymentId) ?: return
        if (dao.group(payment.groupId)?.remote != true) {
            removedHere[paymentId] = payment
            dao.deletePayment(paymentId)
            return
        }
        writeRemoval(payment.groupId, paymentId, removed = true)
    }

    override suspend fun putBackPayment(paymentId: String) {
        removedHere.remove(paymentId)?.let { held ->
            dao.insertPayment(held)
            return
        }
        val payment = dao.payment(paymentId) ?: return
        writeRemoval(payment.groupId, paymentId, removed = false)
    }

    /**
     * A row in the log, uploaded like an expense version. Even a payment not yet sent gets one: its
     * upload may already be under way, and deleting it here would let it come back on the next pull.
     */
    private suspend fun writeRemoval(groupId: String, paymentId: String, removed: Boolean) {
        val id = newId()
        dao.insertPaymentRemoval(
            PaymentRemovalEntity(
                id = id,
                groupId = groupId,
                paymentId = paymentId,
                removed = removed,
                authorMemberId = snapshot.value.group(groupId)?.meMemberId,
                createdAtEpochMillis = clock.now().toEpochMilliseconds(),
            ),
        )
        queueIfShared(groupId, KIND_PAYMENT_REMOVAL, id)
    }

    override suspend fun restoreExpense(expenseId: String) {
        val versions = dao.versionsOf(expenseId)
        if (versions.firstOrNull()?.removed != true) return
        val before = versions.firstOrNull { !it.removed } ?: return
        val now = clock.now().toEpochMilliseconds()
        val versionId = newId()
        dao.writeVersion(
            version = before.copy(
                versionId = versionId,
                version = versions.first().version + 1,
                authorMemberId = snapshot.value.group(before.groupId)?.meMemberId,
                createdAtEpochMillis = now,
                cause = null,
            ),
            shares = dao.sharesOf(before.versionId).map { it.copy(versionId = versionId) },
            newTransaction = null,
        )
        queueIfShared(before.groupId, KIND_VERSION, versionId)
    }

    override suspend fun relink(expenseId: String, transactionId: String) {
        if (dao.transaction(transactionId) != null) dao.upsertLink(ExpenseLinkEntity(expenseId = expenseId, transactionId = transactionId))
    }

    override suspend fun saveRepeat(rule: RepeatRule): String {
        val id = rule.id.ifEmpty { newId() }
        val existing = dao.repeat(id)
        dao.upsertRepeat(
            rule.copy(
                id = id,
                // This phone's progress and reminder choice survive an edit of the template.
                lastAdded = existing?.lastAdded?.let(LocalDate::parse) ?: rule.lastAdded,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: clock.now().toEpochMilliseconds(),
            ).toEntity(),
        )
        queueIfShared(rule.groupId, KIND_REPEAT, id)
        return id
    }

    override suspend fun stopRepeat(ruleId: String) {
        val rule = dao.repeat(ruleId) ?: return
        dao.upsertRepeat(rule.copy(active = false))
        queueIfShared(rule.groupId, KIND_REPEAT, ruleId)
    }

    override suspend fun setRepeatReminder(ruleId: String, on: Boolean) = dao.setRemind(ruleId, on)

    override suspend fun addDueRepeats(today: LocalDate): Int {
        var added = 0
        for (entity in dao.activeRepeats()) {
            val rule = entity.toDomain()
            val group = snapshot.value.group(rule.groupId) ?: continue
            for (date in rule.due(today)) {
                val entry = repeatEntry(rule, group, date)
                if (dao.addRepeatEntry(entry.first, entry.second, rule.id, date.toString())) {
                    added++
                    queueIfShared(rule.groupId, KIND_VERSION, entry.first.versionId)
                }
            }
        }
        return added
    }

    /**
     * The expense a repeat adds on [date]: the template split over today's members. When the
     * people changed since and the old weights no longer add up, it splits equally instead.
     */
    private fun repeatEntry(rule: RepeatRule, group: SharedGroup, date: LocalDate): Pair<ExpenseVersionEntity, List<ExpenseShareEntity>> {
        val members = group.membersToSplit()
        val currency = Currencies.of(rule.currency)
        val satang = toSatang(rule.amountMinor, currency, rule.rate)
        val asSaved = split(rule.amountMinor, rule.splitMode, members, rule.weights)
        val mode = if (asSaved.complete) rule.splitMode else SplitMode.Equally
        val check = if (asSaved.complete) asSaved else split(rule.amountMinor, SplitMode.Equally, members, emptyMap())
        val id = rule.entryId(date)
        val versionId = "$id:1"
        val at = date.atTime(REPEAT_HOUR, 0).toInstant(BangkokTime).toEpochMilliseconds()
        // The log orders by this, so it is the day's start, the same on every phone: an edit made
        // before 09:00 on the day the entry is added still comes after it.
        val loggedAt = date.startOfDayEpochMillis()
        val version = ExpenseVersionEntity(
            versionId = versionId,
            expenseId = id,
            groupId = rule.groupId,
            version = 1,
            description = rule.description,
            occurredAtEpochMillis = at,
            categoryId = rule.categoryId,
            subCategoryId = null,
            note = null,
            currency = currency.code,
            amountMinor = rule.amountMinor,
            rateUnscaled = rule.rate?.unscaled,
            rateScale = rule.rate?.scale,
            amountSatang = satang.toMinorUnits(),
            payerMemberId = rule.payerMemberId,
            splitMode = mode.name,
            authorMemberId = null,
            createdAtEpochMillis = loggedAt,
            removed = false,
            cause = ExpenseCause.REPEAT,
        )
        val parts = shares(check, satang, members, rule.weights, mode)
        return version to parts.map { ExpenseShareEntity(versionId, it.memberId, it.weight, it.amountMinor, it.satang.toMinorUnits()) }
    }

    override suspend fun markSeen(groupId: String, atEpochMillis: Long) = dao.markSeen(groupId, atEpochMillis)

    override suspend fun lastRate(currency: String): Rate? =
        dao.rate(currency)?.let { Rate(it.rateUnscaled, it.rateScale) }

    private companion object {
        /** This phone's own member, until sign-in gives it a name (#187). Screens show "You" for it anyway. */
        const val YOU = "You"
    }
}

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

/** No rate at all, or a whole one [Rate] accepts. Half a rate, a zero or too many decimals is not. */
private fun readableRate(unscaled: Long?, scale: Int?): Boolean = when {
    unscaled == null && scale == null -> true
    unscaled == null || scale == null -> false
    else -> unscaled > 0 && scale in 0..Rate.MAX_SCALE
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

/** #182: the hour a repeat's expense is dated on its Bangkok day. Its place in the log is the day's start. */
private const val REPEAT_HOUR = 9

fun RepeatRuleEntity.toDomain() = RepeatRule(
    id = id,
    groupId = groupId,
    kind = RepeatKind.valueOf(kind),
    frequency = RepeatFrequency.valueOf(frequency),
    start = LocalDate.parse(startDate),
    end = when (endKind) {
        END_ON_DATE -> endDate?.let { RepeatEnd.OnDate(LocalDate.parse(it)) } ?: RepeatEnd.Never
        END_AFTER -> endTimes?.let { RepeatEnd.After(it) } ?: RepeatEnd.Never
        else -> RepeatEnd.Never
    },
    remind = remind,
    active = active,
    lastAdded = lastAdded?.let(LocalDate::parse),
    description = description,
    categoryId = categoryId,
    currency = currency,
    amountMinor = amountMinor,
    rate = if (rateUnscaled != null && rateScale != null) Rate(rateUnscaled, rateScale) else null,
    payerMemberId = payerMemberId,
    splitMode = SplitMode.valueOf(splitMode),
    weights = weights.split(';').filter { '=' in it }.associate { it.substringBefore('=') to (it.substringAfter('=').toLongOrNull() ?: 0L) },
    createdAtEpochMillis = createdAtEpochMillis,
)

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
