package com.ptut.expense.sync

import com.ptut.expense.data.local.dao.SharedDao
import com.ptut.expense.data.local.entity.CurrencyRateEntity
import com.ptut.expense.data.local.entity.ExpenseLinkEntity
import com.ptut.expense.data.local.entity.ExpenseShareEntity
import com.ptut.expense.data.local.entity.ExpenseVersionEntity
import com.ptut.expense.data.local.entity.GroupMemberEntity
import com.ptut.expense.data.local.entity.PaymentReactionEntity
import com.ptut.expense.data.local.entity.PaymentRemovalEntity
import com.ptut.expense.data.local.entity.PersonEntity
import com.ptut.expense.data.local.entity.RepeatRuleEntity
import com.ptut.expense.data.local.entity.SharedGroupEntity
import com.ptut.expense.data.local.entity.SharedPaymentEntity
import com.ptut.expense.data.local.entity.SyncOutboxEntity
import com.ptut.expense.data.local.entity.TransactionEntity
import com.ptut.expense.data.repository.toDomain
import com.ptut.expense.domain.TransactionEdit
import com.ptut.expense.domain.shared.ExpenseDraft
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.PaymentDraft
import com.ptut.expense.domain.shared.Rate
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.TitleChange
import com.ptut.expense.domain.shared.TransactionSeed
import com.ptut.expense.sync.auth.Account
import com.ptut.expense.sync.auth.AuthRepository
import com.ptut.expense.sync.auth.ProviderToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Firestore as a map of paths. A call whose text contains one of [failing] fails as an HTML answer
 * does; a write to a path in [refusing] fails with that status, and a read of a path in [hidden]
 * fails as the rules refuse it. [writes] lists the writes that landed.
 */
internal class FakeFirestore : Firestore {
    val docs = mutableMapOf<String, FirestoreDoc>()
    val collections = mutableMapOf<String, List<FirestoreDoc>>()
    var memberships: List<FirestoreDoc> = emptyList()
    val failing = mutableSetOf<String>()
    val refusing = mutableMapOf<String, Int>()
    val hidden = mutableSetOf<String>()
    val calls = mutableListOf<String>()
    val writes = mutableListOf<String>()

    /** The fields each create sent, by path. */
    val created = mutableMapOf<String, Fields>()

    /** The fields the last patch or set sent, by path. */
    val sent = mutableMapOf<String, Fields>()
    var readTime = 1_000L

    /** Runs before each write, so a test can change the world mid-push. */
    var beforeWrite: (String) -> Unit = {}

    override suspend fun get(path: String): FirestoreDoc? = answer("get $path", path) { docs[path] }

    override suspend fun list(collection: String): List<FirestoreDoc> = answer("list $collection", collection) { collections[collection].orEmpty() }

    override suspend fun whereContains(collection: String, field: String, value: String): List<FirestoreDoc> = answer("query $collection") { memberships }

    override suspend fun changedSince(parent: String, collection: String, field: String, since: Long): Pulled =
        answer("since $parent/$collection") { Pulled(collections["$parent/$collection"].orEmpty(), readTime) }

    override suspend fun create(collection: String, id: String, fields: Fields): Boolean = write("create", "$collection/$id") {
        created["$collection/$id"] = fields
        "$collection/$id" !in docs
    }

    override suspend fun patch(path: String, fields: Fields) = write("patch", path) { sent[path] = fields }

    override suspend fun set(path: String, fields: Fields) = write("set", path) { sent[path] = fields }

    override suspend fun delete(path: String) = write("delete", path) {}

    override suspend fun createStamped(collection: String, id: String, fields: Fields, stamp: String): Boolean =
        write("stamped", "$collection/$id") { "$collection/$id" !in docs }

    override suspend fun upsertStamped(relative: String, fields: Fields, stamp: String) = write("upsert", relative) {}

    /** Refused as a whole when any path it touches is in [refusing]. */
    override suspend fun commit(writes: List<JsonObject>) {
        val paths = writes.map { it.getValue(PATH).jsonPrimitive.content }
        beforeWrite("commit")
        paths.firstNotNullOfOrNull { refusing[it] }?.let { throw FirestoreException(it, "Refused") }
        this.writes += "commit ${writes.joinToString { it.getValue(WRITE).jsonPrimitive.content }}"
    }

    override fun updateWrite(relative: String, fields: Fields, create: Boolean): JsonObject = described("update $relative ${fields.keys}", relative)

    override fun appendWrite(relative: String, field: String, values: List<String>): JsonObject = described("append $relative $field $values", relative)

    override fun removeWrite(relative: String, field: String, values: List<String>, alsoSet: Fields): JsonObject =
        described("remove $relative $field $values" + if (alsoSet.isEmpty()) "" else " set $alsoSet", relative)

    override fun stampedUpdateWrite(relative: String, fields: Fields, stamp: String): JsonObject = described("update $relative ${fields.keys} stamp $stamp", relative)

    override fun deleteWrite(relative: String): JsonObject = described("delete $relative", relative)

    private fun described(write: String, path: String): JsonObject = buildJsonObject {
        put(WRITE, write)
        put(PATH, path)
    }

    private inline fun <T> write(kind: String, path: String, value: () -> T): T {
        beforeWrite("$kind $path")
        refusing[path]?.let { throw FirestoreException(it, "Refused") }
        writes += "$kind $path"
        return value()
    }

    private inline fun <T> answer(call: String, path: String = "", value: () -> T): T {
        calls += call
        if (failing.any { it in call }) throw FirestoreException(FirestoreStatus.NO_ANSWER, "Not a Firestore answer")
        if (path in hidden) throw FirestoreException(FirestoreStatus.FORBIDDEN, "Refused")
        return value()
    }

    private companion object {
        const val WRITE = "write"
        const val PATH = "path"
    }
}

/** Room's shared tables as maps, counting the writes a pull makes. */
internal class FakeSharedDao : SharedDao {

    val groups = mutableMapOf<String, SharedGroupEntity>()
    val members = mutableMapOf<String, GroupMemberEntity>()
    val versions = mutableMapOf<String, ExpenseVersionEntity>()
    val shares = mutableListOf<ExpenseShareEntity>()
    val payments = mutableMapOf<String, SharedPaymentEntity>()
    val removals = mutableMapOf<String, PaymentRemovalEntity>()
    val repeats = mutableMapOf<String, RepeatRuleEntity>()
    val reactions = mutableMapOf<String, PaymentReactionEntity>()
    val outboxRows = MutableStateFlow<List<SyncOutboxEntity>>(emptyList())
    var groupWrites = 0
    var memberWrites = 0
    var marks = 0
    var logWrites = 0
    var reactionWrites = 0

    override fun observeOutbox(): Flow<List<SyncOutboxEntity>> = outboxRows

    override suspend fun outbox(): List<SyncOutboxEntity> = outboxRows.value

    override suspend fun queue(rows: List<SyncOutboxEntity>) {
        outboxRows.update { old -> old.filterNot { row -> rows.any { it.id == row.id } } + rows }
    }

    override suspend fun dequeue(id: String, queuedAt: Long) {
        outboxRows.update { old -> old.filterNot { it.id == id && it.createdAtEpochMillis == queuedAt } }
    }

    override suspend fun queued(outboxId: String): Int = outboxRows.value.count { it.id == outboxId }

    override suspend fun group(groupId: String): SharedGroupEntity? = groups[groupId]

    override suspend fun upsertGroup(group: SharedGroupEntity) {
        groupWrites++
        groups[group.id] = group
    }

    override suspend fun upsertMembers(members: List<GroupMemberEntity>) {
        memberWrites++
        members.forEach { this.members[it.id] = it }
    }

    override suspend fun membersOf(groupId: String): List<GroupMemberEntity> = members.values.filter { it.groupId == groupId }

    override suspend fun markSynced(groupId: String, at: Long) {
        marks++
        groups[groupId]?.let { groups[groupId] = it.copy(lastSyncedAtEpochMillis = at) }
    }

    override suspend fun remoteGroupIds(): List<String> = groups.values.filter { it.remote }.map { it.id }

    override suspend fun receiveLog(
        versions: List<Pair<ExpenseVersionEntity, List<ExpenseShareEntity>>>,
        payments: List<SharedPaymentEntity>,
        removals: List<PaymentRemovalEntity>,
    ) {
        logWrites++
        super.receiveLog(versions, payments, removals)
    }

    override suspend fun insertVersionIfNew(version: ExpenseVersionEntity): Long {
        if (version.versionId in versions) return -1L
        versions[version.versionId] = version
        return 1L
    }

    override suspend fun insertSharesIfNew(shares: List<ExpenseShareEntity>) {
        this.shares += shares
    }

    override suspend fun insertShares(shares: List<ExpenseShareEntity>) {
        this.shares += shares
    }

    override suspend fun insertPaymentIfNew(payment: SharedPaymentEntity) {
        payments.getOrPut(payment.id) { payment }
    }

    override suspend fun insertPaymentRemovalIfNew(removal: PaymentRemovalEntity) {
        removals.getOrPut(removal.id) { removal }
    }

    override suspend fun version(versionId: String): ExpenseVersionEntity? = versions[versionId]

    override suspend fun sharesOf(versionId: String): List<ExpenseShareEntity> = shares.filter { it.versionId == versionId }

    override suspend fun updateVersion(version: ExpenseVersionEntity) {
        versions[version.versionId] = version
    }

    override suspend fun deleteSharesOf(versionId: String) {
        shares.removeAll { it.versionId == versionId }
    }

    override suspend fun deleteLinksOf(groupId: String) = Unit

    override suspend fun deleteVersionsOf(groupId: String) {
        versions.values.removeAll { it.groupId == groupId }
    }

    override suspend fun deletePaymentsOf(groupId: String) {
        payments.values.removeAll { it.groupId == groupId }
    }

    override suspend fun deletePaymentRemovalsOf(groupId: String) {
        removals.values.removeAll { it.groupId == groupId }
    }

    override suspend fun deleteOutboxOf(groupId: String) {
        outboxRows.update { old -> old.filterNot { it.groupId == groupId } }
    }

    override suspend fun deleteGroup(groupId: String) {
        groups.remove(groupId)
        members.values.removeAll { it.groupId == groupId }
    }

    override suspend fun repeat(id: String): RepeatRuleEntity? = repeats[id]

    override suspend fun upsertRepeat(rule: RepeatRuleEntity) {
        repeats[rule.id] = rule
    }

    override fun observeGroups(): Flow<List<SharedGroupEntity>> = error("unused")

    override fun observeMembers(): Flow<List<GroupMemberEntity>> = error("unused")

    override fun observePeople(): Flow<List<PersonEntity>> = error("unused")

    override fun observeVersions(): Flow<List<ExpenseVersionEntity>> = error("unused")

    override fun observeShares(): Flow<List<ExpenseShareEntity>> = error("unused")

    override fun observePayments(): Flow<List<SharedPaymentEntity>> = error("unused")

    override fun observeLinks(): Flow<List<ExpenseLinkEntity>> = error("unused")

    override fun observePaymentRemovals(): Flow<List<PaymentRemovalEntity>> = error("unused")

    override suspend fun setEndedOn(groupId: String, endedOn: String?) {
        groups[groupId]?.let { groups[groupId] = it.copy(endedOn = endedOn) }
    }

    override suspend fun setPlayOff(memberIds: List<String>, off: Boolean) {
        memberIds.forEach { id -> members[id]?.let { members[id] = it.copy(playOff = off) } }
    }

    override fun observeReactions(): Flow<List<PaymentReactionEntity>> = error("unused")

    override suspend fun reaction(id: String): PaymentReactionEntity? = reactions[id]

    override suspend fun upsertReaction(reaction: PaymentReactionEntity) {
        reactions[reaction.id] = reaction
    }

    override suspend fun reactionsOf(groupId: String): List<PaymentReactionEntity> = reactions.values.filter { it.groupId == groupId }

    override suspend fun deleteReactions(ids: List<String>) {
        ids.forEach { reactions.remove(it) }
    }

    override suspend fun upsertReactions(rows: List<PaymentReactionEntity>) {
        reactionWrites++
        rows.forEach { reactions[it.id] = it }
    }

    override suspend fun insertGroup(group: SharedGroupEntity) = error("unused")

    override suspend fun insertMembers(members: List<GroupMemberEntity>) = error("unused")

    override suspend fun upsertPeople(people: List<PersonEntity>) = error("unused")

    override suspend fun setSimplifyDebts(groupId: String, on: Boolean) = error("unused")

    override suspend fun editGroup(groupId: String, name: String, type: String, icon: String) = error("unused")

    override suspend fun setPersonArchived(personId: String, archived: Boolean) = error("unused")

    override suspend fun membersOfPerson(personId: String): List<GroupMemberEntity> = error("unused")

    override suspend fun setPromptPay(memberId: String, promptPayId: String?) = error("unused")

    override suspend fun setMembersArchived(memberIds: List<String>, archived: Boolean) = error("unused")

    override suspend fun latestVersionNumber(expenseId: String): Int? = error("unused")

    override suspend fun latestVersion(expenseId: String): ExpenseVersionEntity? = error("unused")

    override suspend fun versionsOf(expenseId: String): List<ExpenseVersionEntity> = error("unused")

    override suspend fun transaction(id: String): TransactionEntity? = error("unused")

    override suspend fun insertVersion(version: ExpenseVersionEntity) = error("unused")

    override suspend fun insertTransaction(transaction: TransactionEntity) = error("unused")

    override suspend fun upsertLink(link: ExpenseLinkEntity) = error("unused")

    override suspend fun link(expenseId: String): ExpenseLinkEntity? = error("unused")

    override suspend fun unlinkTransaction(transactionId: String) = error("unused")

    override suspend fun insertPayment(payment: SharedPaymentEntity) = error("unused")

    override suspend fun payment(id: String): SharedPaymentEntity? = payments[id]

    override suspend fun deletePayment(id: String) {
        payments.remove(id)
    }

    override suspend fun insertPaymentRemoval(removal: PaymentRemovalEntity) = error("unused")

    override suspend fun latestRemovalAt(paymentId: String): Long? = error("unused")

    override suspend fun paymentRemoval(id: String): PaymentRemovalEntity? = error("unused")

    override suspend fun deletePaymentRemoval(id: String) = error("unused")

    override suspend fun deleteVersion(versionId: String) = error("unused")

    override suspend fun unlinkIfGone(expenseId: String) = error("unused")

    override suspend fun claimMember(memberId: String, name: String, uid: String?) = error("unused")

    override suspend fun setInvite(groupId: String, code: String?, active: Boolean) {
        groups[groupId]?.let { groups[groupId] = it.copy(inviteCode = code, inviteActive = active) }
    }

    override suspend fun markSeen(groupId: String, at: Long) = error("unused")

    override suspend fun people(): List<PersonEntity> = error("unused")

    override suspend fun localGroups(): List<SharedGroupEntity> = error("unused")

    override suspend fun membersIn(groupIds: List<String>): List<GroupMemberEntity> = error("unused")

    override suspend fun versionsIn(groupIds: List<String>): List<ExpenseVersionEntity> = error("unused")

    override suspend fun sharesIn(groupIds: List<String>): List<ExpenseShareEntity> = error("unused")

    override suspend fun paymentsIn(groupIds: List<String>): List<SharedPaymentEntity> = error("unused")

    override suspend fun linksIn(groupIds: List<String>): List<ExpenseLinkEntity> = error("unused")

    override suspend fun heldTransactionIds(ids: List<String>): List<String> = error("unused")

    override suspend fun insertPeopleIfNew(people: List<PersonEntity>) = error("unused")

    override suspend fun insertGroupsIfNew(groups: List<SharedGroupEntity>): List<Long> = error("unused")

    override suspend fun insertMembersIfNew(members: List<GroupMemberEntity>) = error("unused")

    override suspend fun insertPaymentsIfNew(payments: List<SharedPaymentEntity>) = error("unused")

    override suspend fun insertLinksIfNew(links: List<ExpenseLinkEntity>) = error("unused")

    override fun observeRepeats(): Flow<List<RepeatRuleEntity>> = error("unused")

    override suspend fun activeRepeats(): List<RepeatRuleEntity> = error("unused")

    override suspend fun setLastAdded(id: String, date: String) = error("unused")

    override suspend fun setRemind(id: String, on: Boolean) = error("unused")

    override suspend fun repeatsIn(groupIds: List<String>): List<RepeatRuleEntity> = error("unused")

    override suspend fun insertRepeatsIfNew(rules: List<RepeatRuleEntity>) = error("unused")

    override suspend fun rate(currency: String, home: String): CurrencyRateEntity? = error("unused")

    override suspend fun upsertRate(rate: CurrencyRateEntity) = error("unused")
}

/** The snapshot the status line reads, built from [dao] each time it is asked. */
internal class FakeSharedRepository(private val dao: FakeSharedDao) : SharedRepository {
    override val snapshot: StateFlow<SharedSnapshot>
        get() = MutableStateFlow(
            SharedSnapshot(
                groups = dao.groups.values.map { group -> group.toDomain(dao.members.values.filter { it.groupId == group.id }) },
                people = emptyList(),
                expenseVersions = emptyList(),
                payments = emptyList(),
                links = emptyMap(),
            ),
        )

    override suspend fun createGroup(name: String, type: GroupType, icon: String, members: List<NewMember>, currency: String): String = error("unused")

    override suspend fun addMembers(groupId: String, members: List<NewMember>) = error("unused")

    override suspend fun setSimplifyDebts(groupId: String, on: Boolean) = error("unused")

    override suspend fun setPromptPay(groupId: String, promptPayId: String?) = error("unused")

    override suspend fun editGroup(groupId: String, name: String, type: GroupType, icon: String) = error("unused")

    override suspend fun deleteGroup(groupId: String) = error("unused")

    override suspend fun setPersonArchived(personId: String, archived: Boolean) = error("unused")

    override suspend fun removeMember(groupId: String, memberId: String) = error("unused")

    override suspend fun saveExpense(draft: ExpenseDraft): String = error("unused")

    override suspend fun removeExpense(expenseId: String) = error("unused")

    override suspend fun recordPayment(draft: PaymentDraft): String = error("unused")

    override suspend fun transactionSeed(transactionId: String): TransactionSeed? = error("unused")

    override suspend fun followTransaction(transactionId: String, before: TransactionEdit, titleChange: TitleChange?) = error("unused")

    override suspend fun removePayment(paymentId: String) = error("unused")

    override suspend fun putBackPayment(paymentId: String) = error("unused")

    override suspend fun restoreExpense(expenseId: String) = error("unused")

    override suspend fun relink(expenseId: String, transactionId: String) = error("unused")

    override suspend fun saveRepeat(rule: RepeatRule): String = error("unused")

    override suspend fun stopRepeat(ruleId: String) = error("unused")

    override suspend fun setRepeatReminder(ruleId: String, on: Boolean) = error("unused")

    override suspend fun addDueRepeats(today: LocalDate): Int = error("unused")

    override suspend fun lastRate(currency: String, into: String): Rate? = error("unused")

    override suspend fun markSeen(groupId: String, atEpochMillis: Long) = error("unused")
}

internal class FakeAuth(uid: String) : AuthRepository {
    val signedIn = Account.SignedIn(uid, "Mali", null, "google.com")
    override val available: Boolean = true
    override val account = MutableStateFlow<Account>(signedIn)

    // No auth_time in it, so the session check decides "this phone holds it" without a request.
    override suspend fun idToken(): String = "token"

    override suspend fun signIn(token: ProviderToken): Account.SignedIn = error("unused")

    override suspend fun signOut() {
        account.value = Account.SignedOut
    }

    override suspend fun deleteAccount() {
        account.value = Account.SignedOut
    }
}

internal class TestClock(var nowMillis: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(nowMillis)
}
