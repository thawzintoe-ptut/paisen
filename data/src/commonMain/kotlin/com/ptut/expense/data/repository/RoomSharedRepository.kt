package com.ptut.expense.data.repository

import com.ptut.expense.data.local.dao.SharedDao
import com.ptut.expense.data.local.dao.TransactionDao
import com.ptut.expense.data.local.dao.withCheckedSubCategory
import com.ptut.expense.data.local.entity.CurrencyRateEntity
import com.ptut.expense.data.local.entity.ExpenseLinkEntity
import com.ptut.expense.data.local.entity.ExpenseShareEntity
import com.ptut.expense.data.local.entity.ExpenseVersionEntity
import com.ptut.expense.data.local.entity.GroupMemberEntity
import com.ptut.expense.data.local.entity.PaymentReactionEntity
import com.ptut.expense.data.local.entity.PaymentRemovalEntity
import com.ptut.expense.data.local.entity.PersonEntity
import com.ptut.expense.data.local.entity.SharedGroupEntity
import com.ptut.expense.data.local.entity.SharedPaymentEntity
import com.ptut.expense.data.local.entity.SyncOutboxEntity
import com.ptut.expense.data.local.entity.toEntity
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.TransactionDraft
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.ledger.LedgerRepository
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.ExpenseCause
import com.ptut.expense.domain.shared.ExpenseChanged
import com.ptut.expense.domain.shared.ExpenseDraft
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.PaymentDraft
import com.ptut.expense.domain.shared.Person
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.ReactionKind
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.Share
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.SplitMode
import com.ptut.expense.domain.shared.TitleChange
import com.ptut.expense.domain.shared.TransactionSeed
import com.ptut.expense.domain.shared.convert
import com.ptut.expense.domain.shared.due
import com.ptut.expense.domain.shared.entryId
import com.ptut.expense.domain.shared.followed
import com.ptut.expense.domain.shared.membersToSplit
import com.ptut.expense.domain.shared.money
import com.ptut.expense.domain.shared.sharedExpenseOf
import com.ptut.expense.domain.shared.shares
import com.ptut.expense.domain.shared.split
import com.ptut.expense.domain.shared.splitMembers
import com.ptut.expense.domain.shared.toLocalDateOrNull
import com.ptut.expense.domain.startOfDayEpochMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
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
        combine(dao.observeGroups().distinctUntilChanged(), dao.observeMembers().distinctUntilChanged(), dao.observePeople().distinctUntilChanged()) { groups, members, people ->
            val byGroup = members.groupBy { it.groupId }
            groups.map { it.toDomain(byGroup[it.id].orEmpty()) } to people.map { Person(it.id, it.name, it.archived) }
        },
        combine(dao.observeVersions().distinctUntilChanged(), dao.observeShares().distinctUntilChanged()) { versions, shares ->
            val byVersion = shares.groupBy { it.versionId }
            versions.mapNotNull { it.toDomain(byVersion[it.versionId].orEmpty()) }
        },
        combine(dao.observePayments().distinctUntilChanged(), dao.observePaymentRemovals().distinctUntilChanged(), dao.observeReactions().distinctUntilChanged(), ::Triple),
        dao.observeLinks().distinctUntilChanged(),
        dao.observeRepeats().distinctUntilChanged(),
    ) { (groups, people), versions, (payments, removals, reactions), links, repeats ->
        SharedSnapshot(
            groups = groups,
            people = people,
            expenseVersions = versions,
            payments = payments.mapNotNull { it.toDomain() },
            links = links.associate { it.expenseId to it.transactionId },
            repeats = repeats.mapNotNull { it.toDomain() },
            paymentRemovals = removals.map { it.toDomain() },
            reactions = reactions.mapNotNull { it.toDomain() },
        )
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, SharedSnapshot.Empty)

    override suspend fun createGroup(name: String, type: GroupType, icon: String, members: List<NewMember>, currency: String): String {
        val now = clock.now().toEpochMilliseconds()
        val groupId = newId()
        val meId = newId()
        val (people, rows) = membersOf(groupId, members, now)
        dao.createGroup(
            group = SharedGroupEntity(groupId, name.trim(), type.name, icon, simplifyDebts = true, createdAtEpochMillis = now, meMemberId = meId, currency = currency),
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

    /**
     * The time a new log row is stamped with. Every phone takes the row with the latest stamp as
     * current (LogOrder, RemovalOrder), so a row written after [latestAt] must sort after it even when
     * this phone's clock is behind the one that wrote [latestAt].
     */
    private fun stampAfter(latestAt: Long?): Long {
        val now = clock.now().toEpochMilliseconds()
        return if (latestAt == null || now > latestAt) now else latestAt + 1
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

    override suspend fun setTripEnded(groupId: String, on: LocalDate?) {
        dao.setEndedOn(groupId, on?.toString())
        queueIfShared(groupId, KIND_TRIP_END, groupId)
    }

    override suspend fun setStampsOff(off: Boolean) {
        val mine = snapshot.value.groups.mapNotNull { group -> group.meMemberId?.let { group.id to it } }
        dao.setPlayOff(mine.map { it.second }, off)
        mine.forEach { (groupId, memberId) -> queueIfShared(groupId, KIND_PLAY, memberId) }
    }

    override suspend fun react(groupId: String, paymentId: String, kind: ReactionKind?) {
        val group = snapshot.value.group(groupId)?.takeIf { it.remote } ?: return
        // The name this account claimed: a reaction is always signed by its own account.
        val uid = group.meMemberId?.let(group::member)?.claimedByUid ?: return
        val id = "${paymentId}_$uid"
        // Taken back: the row stays as Gone until it is sent, so every other phone learns it went (#95).
        if (kind == null && dao.reaction(id) == null) return
        dao.upsertReaction(PaymentReactionEntity(id, groupId, paymentId, uid, kind?.name ?: REACTION_GONE, clock.now().toEpochMilliseconds()))
        queueIfShared(groupId, KIND_REACTION, id)
    }

    override suspend fun saveExpense(draft: ExpenseDraft): String {
        val group = requireNotNull(snapshot.value.group(draft.groupId)) { "No group ${draft.groupId}" }
        val now = clock.now().toEpochMilliseconds()
        val currency = Currencies.of(draft.currency)
        // #48: [satang] holds the group's own money now, whatever that is; baht for every group before #48.
        val satang = convert(draft.amountMinor, currency, draft.rate, group.money)
        val latest = draft.expenseId?.let { dao.latestVersion(it) }
        if (latest != null && draft.baseVersionId != null && latest.versionId != draft.baseVersionId) {
            throw ExpenseChanged(latest.versionId, latest.removed)
        }
        val sharers = latest?.let { dao.sharesOf(it.versionId) }.orEmpty()
        val members = group.membersToSplit(draft.splitMode, sharers = sharers.mapTo(mutableSetOf()) { it.memberId })
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
            createdAtEpochMillis = stampAfter(latest?.createdAtEpochMillis),
            removed = false,
        )
        val link = dao.link(expenseId)
        val youPaid = group.isMe(draft.payerMemberId)
        // A group in other money keeps no own transaction (#48) until a second rate is asked.
        val inHome = group.currency == Home.money.code
        val shareOf = draft.linkTransactionId?.takeIf { link == null }
        // #186: a transaction is shared once at most. A link left by a removed expense gives way.
        if (shareOf != null) {
            require(snapshot.value.sharedExpenseOf(shareOf) == null) { "Transaction $shareOf is already shared" }
            dao.unlinkTransaction(shareOf)
        }
        dao.writeVersion(
            version = row,
            shares = shares.map { it.toEntity(versionId) },
            newTransaction = if (youPaid && inHome && link == null && shareOf == null && draft.categoryId != null) yourTransaction(draft, satang, now) else null,
        )
        if (shareOf != null) dao.upsertLink(ExpenseLinkEntity(expenseId = expenseId, transactionId = shareOf))
        // #32: the full amount you paid stays your own transaction's amount after an edit too.
        if (youPaid && inHome && link != null && draft.categoryId != null) {
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
        if (draft.rate != null) dao.upsertRate(CurrencyRateEntity(currency.code, draft.rate.unscaled, draft.rate.scale, now, group.currency))
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
        val versionId = newId()
        val group = snapshot.value.group(last.groupId)
        dao.writeVersion(
            version = last.next(versionId, group?.meMemberId).copy(removed = true),
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

    override suspend fun followTransaction(transactionId: String, before: TransactionEdit, titleChange: TitleChange?) {
        val expenseId = snapshot.value.links.entries.firstOrNull { it.value == transactionId }?.key ?: return
        val last = dao.latestVersion(expenseId)?.takeUnless { it.removed } ?: return
        val row = dao.transaction(transactionId) ?: return
        val group = snapshot.value.group(last.groupId) ?: return
        // Your row follows only your own spending: once someone else pays it, it is theirs to change.
        if (!group.isMe(last.payerMemberId)) return
        val expense = last.toDomain(dao.sharesOf(last.versionId)) ?: return
        val after = TransactionEdit(
            amount = Satang.ofMinorUnits(row.amountMinor),
            categoryId = row.categoryId,
            memo = row.memo,
            subCategoryId = row.subCategoryId,
            occurredAtEpochMillis = row.occurredAtEpochMillis,
        )
        val followed = expense.followed(before, after, titleChange, group.splitMembers(), group.currency, Home.money.code) ?: return
        val versionId = newId()
        dao.writeVersion(
            version = last.next(versionId, group.meMemberId).copy(
                occurredAtEpochMillis = followed.occurredAtEpochMillis,
                categoryId = followed.categoryId,
                subCategoryId = followed.subCategoryId,
                description = followed.description,
                note = followed.note,
                amountMinor = followed.amountMinor,
                amountSatang = followed.satang.toMinorUnits(),
                splitMode = followed.splitMode.name,
                cause = ExpenseCause.TRANSACTION,
            ),
            shares = followed.shares.map { it.toEntity(versionId) },
            newTransaction = null,
        )
        queueIfShared(last.groupId, KIND_VERSION, versionId)
    }

    /** The version after [latest], written by this phone and stamped after it, with [this] version's fields. */
    private fun ExpenseVersionEntity.next(versionId: String, authorMemberId: String?, latest: ExpenseVersionEntity = this) = copy(
        versionId = versionId,
        version = latest.version + 1,
        authorMemberId = authorMemberId,
        createdAtEpochMillis = stampAfter(latest.createdAtEpochMillis),
    )

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
                createdAtEpochMillis = stampAfter(dao.latestRemovalAt(paymentId)),
            ),
        )
        queueIfShared(groupId, KIND_PAYMENT_REMOVAL, id)
    }

    override suspend fun restoreExpense(expenseId: String) {
        val versions = dao.versionsOf(expenseId)
        if (versions.firstOrNull()?.removed != true) return
        val before = versions.firstOrNull { !it.removed } ?: return
        val versionId = newId()
        dao.writeVersion(
            version = before.next(versionId, snapshot.value.group(before.groupId)?.meMemberId, latest = versions.first()).copy(cause = null),
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
                lastAdded = existing?.lastAdded?.toLocalDateOrNull() ?: rule.lastAdded,
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
            val rule = entity.toDomain() ?: continue
            val group = snapshot.value.group(rule.groupId) ?: continue
            // One repeat that cannot be added, a synced one with no rate for its money, leaves the others to run.
            runSuspendCatching { added += addDue(rule, group, today) }
        }
        return added
    }

    private suspend fun addDue(rule: RepeatRule, group: SharedGroup, today: LocalDate): Int {
        var added = 0
        for (date in rule.due(today)) {
            val entry = repeatEntry(rule, group, date)
            if (dao.addRepeatEntry(entry.first, entry.second, rule.id, date.toString())) {
                added++
                queueIfShared(rule.groupId, KIND_VERSION, entry.first.versionId)
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
        val satang = convert(rule.amountMinor, currency, rule.rate, group.money)
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
        return version to parts.map { it.toEntity(versionId) }
    }

    override suspend fun markSeen(groupId: String, atEpochMillis: Long) = dao.markSeen(groupId, atEpochMillis)

    override suspend fun lastRate(currency: String, into: String): Rate? =
        dao.rate(currency, into)?.let { Rate(it.rateUnscaled, it.rateScale) }

    private companion object {
        /** This phone's own member, until sign-in gives it a name (#187). Screens show "You" for it anyway. */
        const val YOU = "You"
    }
}

private fun Share.toEntity(versionId: String) = ExpenseShareEntity(
    versionId = versionId,
    memberId = memberId,
    weight = weight,
    amountMinor = amountMinor,
    amountSatang = satang.toMinorUnits(),
)

/** #182: the hour a repeat's expense is dated on its Bangkok day. Its place in the log is the day's start. */
private const val REPEAT_HOUR = 9
