package com.ptut.expense.sync

import com.ptut.expense.data.local.dao.SharedDao
import com.ptut.expense.data.local.entity.RefusedChangeEntity
import com.ptut.expense.data.local.entity.SyncOutboxEntity
import com.ptut.expense.data.repository.KIND_GROUP
import com.ptut.expense.data.repository.KIND_MEMBER
import com.ptut.expense.data.repository.KIND_PAYMENT
import com.ptut.expense.data.repository.KIND_PAYMENT_REMOVAL
import com.ptut.expense.data.repository.KIND_PLAY
import com.ptut.expense.data.repository.KIND_REACTION
import com.ptut.expense.data.repository.KIND_REPEAT
import com.ptut.expense.data.repository.KIND_TRIP_END
import com.ptut.expense.data.repository.KIND_VERSION
import com.ptut.expense.data.repository.toDomain
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.moneyFormat
import com.ptut.expense.sync.auth.Account
import com.ptut.expense.sync.push.PushNotice
import com.ptut.expense.sync.push.PushNotices
import com.ptut.expense.sync.push.PushSender
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject

/**
 * Sends the outbox to Firestore and tells the group what went up. Called under [SharedSync]'s lock.
 * [failed] is the status line's set of groups that could not be reached, shared with [SharedSync].
 */
internal class OutboxPusher(
    private val firestore: Firestore,
    private val dao: SharedDao,
    private val repository: SharedRepository,
    private val clock: Clock,
    /** Null in a build with no push Worker: no notice is built or sent. */
    private val pushes: PushSender?,
    private val failed: MutableStateFlow<Set<String>>,
    /** The signed-in account; throws as a lost session does when there is none. */
    private val account: () -> Account.SignedIn,
    /** Makes a fresh invite for a group, so a removed member's old link stops working. */
    private val newInvite: suspend (groupId: String) -> String,
    /** Puts a group's pickable names on its live invite, after they changed here (L4). */
    private val publishNames: suspend (groupId: String) -> Unit = {},
) {
    /**
     * [GroupSyncStatus.refused] per group: changes the group would not take (#194), one entry each,
     * kept in Room so a restart does not lose them. An expense is named, so the user knows what to
     * enter again; any other change is blank.
     */
    val refused: Flow<Map<String, List<String>>> =
        dao.observeRefused().map { rows -> rows.groupBy({ it.groupId }, { it.title }) }

    suspend fun acknowledgeRefused(groupId: String) = dao.clearRefused(groupId)

    /**
     * #194: a change the server refuses for good (a 4xx other than an expired session, a timeout
     * or a rate limit) leaves the outbox and is counted once; anything that may pass later stays.
     * A row that has to wait holds back the rest of its group, so a group's changes go up in the
     * order they were made and no entry lands before the name that signs it.
     */
    suspend fun push(quietFor: String? = null) {
        val held = mutableSetOf<String>()
        // Groups where a name nobody holds was added, renamed or removed: their invite lists it once, after the loop.
        val namesChanged = mutableSetOf<String>()
        for (row in dao.outbox()) {
            if (row.groupId in held) continue
            try {
                val notice = upload(row, notify = row.groupId != quietFor)
                dao.dequeue(row.id, row.createdAtEpochMillis)
                failed.update { it - row.groupId }
                if (row.kind == KIND_MEMBER && dao.membersOf(row.groupId).any { it.id == row.rowId && it.claimedByUid == null }) namesChanged += row.groupId
                notice?.let { pushes?.tell(row.groupId, it) }
            } catch (refusal: FirestoreException) {
                if (refusal.isPermanent()) {
                    // Kept before the change leaves this phone, so what was typed is never lost unseen.
                    dao.insertRefused(refusedChange(row))
                    dao.dequeue(row.id, row.createdAtEpochMillis)
                    takeBack(row)
                } else {
                    failed.update { it + row.groupId }
                    held += row.groupId
                }
            }
        }
        namesChanged.forEach { publishNames(it) }
    }

    private suspend fun refusedChange(row: SyncOutboxEntity): RefusedChangeEntity {
        val version = if (row.kind == KIND_VERSION) dao.version(row.rowId) else null
        val payment = if (row.kind == KIND_PAYMENT) dao.payment(row.rowId) else null
        return RefusedChangeEntity(
            id = row.id,
            groupId = row.groupId,
            title = version?.description.orEmpty(),
            amountMinor = version?.amountMinor ?: payment?.amountSatang,
            currency = version?.currency ?: payment?.let { dao.group(row.groupId)?.currency },
            refusedAtEpochMillis = now(),
        )
    }

    /** From Room, as [upload] reads them: what was saved just before Share may not be in the snapshot yet. */
    suspend fun queueWholeGroup(groupId: String) {
        val at = now()
        // The names go a moment earlier: the rules check each entry's author against a name already there.
        val after = at + 1
        val ids = listOf(groupId)
        dao.queue(
            dao.membersOf(groupId).map { SyncOutboxEntity("$KIND_MEMBER:${it.id}", groupId, KIND_MEMBER, it.id, at) } +
                dao.versionsIn(ids).map { SyncOutboxEntity("$KIND_VERSION:${it.versionId}", groupId, KIND_VERSION, it.versionId, after) } +
                dao.paymentsIn(ids).map { SyncOutboxEntity("$KIND_PAYMENT:${it.id}", groupId, KIND_PAYMENT, it.id, after) } +
                dao.repeatsIn(ids).map { SyncOutboxEntity("$KIND_REPEAT:${it.id}", groupId, KIND_REPEAT, it.id, after) },
        )
    }

    /** The notices for [groupId] as the snapshot holds it, or null when no push is sent. */
    fun noticesFor(groupId: String): PushNotices? = pushes?.let { repository.snapshot.value.group(groupId)?.let(::noticesFor) }

    /**
     * A log row the group refused for good reaches no other phone, so it leaves this one too and
     * the balances here match everyone's. A group or name change needs nothing: the next pull
     * writes the server's values over it once it no longer waits.
     */
    private suspend fun takeBack(row: SyncOutboxEntity) {
        when (row.kind) {
            KIND_VERSION -> dao.takeBackVersion(row.rowId)
            KIND_PAYMENT -> dao.deletePayment(row.rowId)
            KIND_PAYMENT_REMOVAL -> dao.deletePaymentRemoval(row.rowId)
        }
    }

    /**
     * A new name is created whole. One already on the server changes field by field, each through
     * the one rule that allows it, so a rename, a removal or a PromptPay ID is never refused whole.
     */
    private suspend fun uploadMember(group: SharedGroup, member: Member, uid: String, notices: PushNotices?): PushNotice? {
        val own = member.id == group.meMemberId
        val fields = mapOf("name" to member.name, "claimedByUid" to member.claimedByUid, "archived" to member.archived, "createdAt" to now())
        if (firestore.create("groups/${group.id}/members", member.id, fields)) {
            if (own) storePromptPay(group.id, member, uid, clearName = false)
            return notices?.personAdded(member.name)?.takeUnless { own }
        }
        val path = "groups/${group.id}/members/${member.id}"
        val creator = group.creatorUid == uid
        // Read first to word the notice (a PromptPay ID alone tells nobody), and to learn whose
        // account a name the creator removes was held by.
        val before = if (notices != null || (creator && member.archived)) firestore.get(path)?.fields else null
        firestore.patch(path, mapOf("name" to member.name))
        // Only the creator removes a name (#190), so no other phone sends one back.
        if (creator) archive(group.id, member.id, member.archived, holder = before?.string("claimedByUid")?.takeIf { it != uid })
        if (own) storePromptPay(group.id, member, uid, clearName = true)
        if (notices == null || before == null) return null
        val wasArchived = before.bool("archived")
        return when {
            creator && member.archived && !wasArchived -> notices.personRemoved(member.name)
            creator && !member.archived && wasArchived -> notices.personAdded(member.name)
            before.string("name") != member.name -> notices.nameChanged()
            else -> null
        }
    }

    /**
     * #199: only this phone's own name carries a PromptPay ID; nobody writes another's. M5: it goes
     * where only members read, and [clearName] empties the field on the name that builds before M5 used.
     */
    private suspend fun storePromptPay(groupId: String, member: Member, uid: String, clearName: Boolean) {
        val path = "groups/$groupId/pay/${member.id}"
        when (val id = member.promptPayId) {
            null -> if (clearName) firestore.delete(path)
            else -> firestore.set(path, mapOf("promptPay" to id, "claimedByUid" to uid))
        }
        if (clearName) firestore.patch("groups/$groupId/members/${member.id}", mapOf("promptPay" to null))
    }

    /**
     * Removing a name someone claimed also takes their account out of the group, after a new invite
     * replaces the live one, so they can no longer read it, write to it or come back on the old link.
     */
    private suspend fun archive(groupId: String, memberId: String, archived: Boolean, holder: String?) {
        if (!archived || holder == null) {
            firestore.patch("groups/$groupId/members/$memberId", mapOf("archived" to archived))
            return
        }
        if (dao.group(groupId)?.inviteActive == true) newInvite(groupId)
        // One commit: the rules refuse a claimed name archived without its account taken out.
        firestore.commit(firestore.leaveWrites(groupId, memberId, holder, firestore.reactionIdsOf(groupId, holder), archive = true))
    }

    /** The notice for what went up, or null when it was already there, tells nobody, or [notify] is off. */
    private suspend fun upload(row: SyncOutboxEntity, notify: Boolean): PushNotice? {
        // Room, not the snapshot: a row saved a moment ago may not be in the snapshot yet, and a
        // null here takes it out of the outbox for good.
        val group = dao.groupOf(row.groupId) ?: return null
        val uid = account().uid
        // Null when nothing will be sent, so the reads that word a notice are skipped too.
        val notices = pushes?.takeIf { notify }?.let { noticesFor(group) }
        return when (row.kind) {
            KIND_GROUP -> {
                // A row queued before per-field rows has no field and sends the whole group, as it always did.
                val field = row.rowId.substringAfter('#', missingDelimiterValue = "")
                val all = group.fields()
                val sent = if (field.isEmpty()) all else all.filterKeys { it == field }
                // Only a new name is news; an icon or the debt setting tells nobody.
                val before = notices?.takeIf { "name" in sent }?.let { firestore.get("groups/${group.id}")?.fields?.string("name") }
                if (sent.isNotEmpty()) firestore.patch("groups/${group.id}", sent)
                notices?.takeIf { before != null && before != group.name }?.renamed()
            }

            KIND_MEMBER -> group.member(row.rowId)?.let { member -> uploadMember(group, member, uid, notices) }

            // The server stamps each entry, so every phone pulls only what came after its last pull.
            KIND_VERSION -> dao.version(row.rowId)?.let { it.toDomain(dao.sharesOf(it.versionId)) }?.let { version ->
                val created = firestore.createStamped("groups/${group.id}/entries", version.versionId, version.fields(uid, group.currency), SYNCED_AT)
                notices?.takeIf { created }?.expense(version, dao.versionsOf(version.id).mapNotNull { it.toDomain(emptyList()) })
            }

            KIND_PAYMENT -> dao.payment(row.rowId)?.toDomain()?.let { payment ->
                val created = firestore.createStamped("groups/${group.id}/entries", payment.id, payment.fields(uid, group.currency), SYNCED_AT)
                notices?.takeIf { created }?.payment(payment)
            }

            // #218: a removal is a document of its own; the payment's document never changes.
            KIND_PAYMENT_REMOVAL -> dao.paymentRemoval(row.rowId)?.toDomain()?.let { removal ->
                val created = firestore.createStamped("groups/${group.id}/entries", removal.id, removal.fields(uid), SYNCED_AT)
                notices?.takeIf { created }?.paymentRemoval(removal)
            }

            // #97: only the end, so a patch from this build never carries a key older rules refuse.
            KIND_TRIP_END -> {
                firestore.patch("groups/${group.id}", mapOf("endedOn" to group.endedOn?.toString()))
                null
            }

            // #96 (D5): on the name this account holds; the rules refuse it on anyone else's.
            KIND_PLAY -> group.member(row.rowId)?.takeIf { it.claimedByUid == uid }?.let { member ->
                firestore.set("groups/${group.id}/play/${member.id}", mapOf("awardsOff" to member.playOff, "claimedByUid" to uid))
                null
            }

            // #95 (D16): no notice for a reaction. One taken back goes up as Gone, so every pull since learns
            // it went; a row gone from Room altogether has nothing to say, so its document goes too.
            KIND_REACTION -> {
                val path = "groups/${group.id}/reactions/${row.rowId}"
                val reaction = dao.reaction(row.rowId)
                if (reaction == null) {
                    firestore.delete(path)
                } else {
                    firestore.upsertStamped(path, reaction.fields(), SYNCED_AT)
                }
                null
            }

            // #182: the template only. Each phone keeps its own progress and reminder choice. The
            // creation time it sends is the stored one, which the rules require to stay put.
            KIND_REPEAT -> dao.repeat(row.rowId)?.let { stored ->
                val rule = stored.toDomain() ?: return@let null
                val path = "groups/${group.id}/repeats/${rule.id}"
                val server = firestore.get(path)
                // A stop is never undone, so an edit made offline before another member stopped it stays stopped.
                if (rule.active && server?.fields?.get("active") == false) {
                    dao.upsertRepeat(stored.copy(active = false))
                    return@let null
                }
                firestore.upsertStamped(path, rule.fields(uid), SYNCED_AT)
                notices?.repeat(rule, server == null)
            }

            else -> null
        }
    }

    private fun noticesFor(group: SharedGroup): PushNotices {
        // The name this phone's member has in the group, which is how the others see it.
        val actor = group.meMemberId?.let(group::member)?.name ?: account().name.firstWord()
        return PushNotices(group.name, actor, group.members, money = group.moneyFormat())
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()
}

/** A group and its members as Room holds them now, which the snapshot may not show yet. */
internal suspend fun SharedDao.groupOf(groupId: String): SharedGroup? = group(groupId)?.toDomain(membersOf(groupId))

/**
 * [uid] lets go of [memberId] in [groupId] and leaves it, in one commit; with [archive] the name
 * is removed too, which the rules allow only together with its account going. [closeInvite]: the
 * creator leaving for good switches the invite off, since nobody else can (L2). [reactionIds] are
 * [uid]'s reactions there, which go with the account (#95).
 */
internal fun Firestore.leaveWrites(
    groupId: String,
    memberId: String?,
    uid: String,
    reactionIds: List<String> = emptyList(),
    archive: Boolean = false,
    closeInvite: Boolean = false,
): List<JsonObject> =
    listOfNotNull(
        memberId?.let {
            val removed = if (archive) mapOf("archived" to true) else emptyMap()
            updateWrite("groups/$groupId/members/$it", removed + mapOf("claimedByUid" to null, "promptPay" to null))
        },
        // M5: the name's PromptPay ID and D5 opt-out go with its claim; the rules let a missing one pass.
        memberId?.let { deleteWrite("groups/$groupId/pay/$it") },
        memberId?.let { deleteWrite("groups/$groupId/play/$it") },
    ) + reactionIds.map { deleteWrite("groups/$groupId/reactions/$it") } +
        removeWrite("groups/$groupId", "memberUids", listOf(uid), alsoSet = if (closeInvite) mapOf("inviteActive" to false) else emptyMap())

private fun FirestoreException.isPermanent(): Boolean = FirestoreStatus.isPermanent(status)
