package com.ptut.expense.sync

import com.ptut.expense.data.local.WriteTransaction
import com.ptut.expense.data.local.dao.SharedDao
import com.ptut.expense.data.local.entity.GroupMemberEntity
import com.ptut.expense.data.local.entity.SharedGroupEntity
import com.ptut.expense.data.local.entity.SyncOutboxEntity
import com.ptut.expense.data.repository.KIND_GROUP
import com.ptut.expense.data.repository.KIND_MEMBER
import com.ptut.expense.data.repository.KIND_PLAY
import com.ptut.expense.data.repository.KIND_REACTION
import com.ptut.expense.data.repository.KIND_REPEAT
import com.ptut.expense.data.repository.KIND_TRIP_END
import com.ptut.expense.data.repository.groupFieldRow
import com.ptut.expense.data.repository.toDomain
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.PromptPay
import com.ptut.expense.domain.shared.entryIdPrefix
import com.ptut.expense.domain.shared.restoredLastAdded
import com.ptut.expense.domain.shared.toLocalDateOrNull
import com.ptut.expense.locale.AppText
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** What a pull did with its group: still here, or gone from this phone. */
internal enum class PullResult { Kept, Removed }

/**
 * Pulls one shared group's document, names and log into Room. Called under [SharedSync]'s lock,
 * which also guards the in-memory marks below.
 */
internal class GroupPuller(
    private val firestore: Firestore,
    private val dao: SharedDao,
    private val clock: Clock,
    /** Puts a group's pickable names on its live invite, after they changed here (L4). */
    private val publishNames: suspend (groupId: String) -> Unit = {},
    /** One Room write transaction, so a merge and a save never interleave; none in tests. */
    private val writes: WriteTransaction = WriteTransaction.None,
    /** The signed-in account, or null; a group brought back finds "you" by the name it claimed. */
    private val uid: () -> String?,
) {
    private class Merged(val namesChanged: Boolean)

    // In memory only: a new process checks everything once.
    private val seenGroupDocs = mutableMapOf<String, Fields>()
    private val membersListedAt = mutableMapOf<String, Long>()

    /** The next account lists every group's names again. */
    fun forget() {
        seenGroupDocs.clear()
        membersListedAt.clear()
    }

    /**
     * Everything another member's phone has written, added to this one. [known] is the group document
     * when a query already read it; [meMemberId] is set when joining. [PullResult.Removed] when the
     * group was deleted for everyone, or this account was taken out of it, and it has gone from this phone.
     * [withReactions] false skips the reactions query on a later pull, to save a read on most timed polls.
     */
    suspend fun pull(groupId: String, known: FirestoreDoc? = null, meMemberId: String? = null, withReactions: Boolean = true): PullResult {
        val remote = known ?: try {
            firestore.get("groups/$groupId")
        } catch (refused: FirestoreException) {
            // The creator took this account out of the group: nothing in it can be read here any more.
            if (refused.status != FirestoreStatus.FORBIDDEN) throw refused
            return forgetKeepingUnsent(groupId)
        } ?: return goneFromServer(groupId)
        val fields = remote.fields
        // The creator deleted it for everyone: it goes from this phone too. Your own spending stays.
        if (fields.bool("deleted")) return forgetKeepingUnsent(groupId)
        val before = dao.group(groupId)
        // #48: set when the group was made and never changed; a group from before #48 is baht.
        val currency = fields.string("currency") ?: before?.currency ?: Currencies.Thb.code
        val pulledUpTo = PullMark.kept(before?.currency, before?.lastSyncedAtEpochMillis, currency)
        val since = pullSince(pulledUpTo)
        val log = if (since == null) wholeLog(groupId) else newerLog(groupId, since)
        val members = if (membersDue(groupId, fields, before, meMemberId, log)) firestore.list("groups/$groupId/members") else null
        val payIds = if (members != null) firestore.promptPayDocs(groupId).associate { it.id to it.fields } else emptyMap()
        // #96 (D5): the opt-outs travel with the names, on the same cadence.
        val playOffs = if (members != null) firestore.playDocs(groupId).associate { it.id to it.fields } else emptyMap()
        // #95 (D16): the whole list once, then only what the server stamped after the mark, which costs a
        // read only for new ones. A taken-back reaction keeps its document as Gone, so it comes the same way.
        // Reactions are asked for on fewer polls than the log, so they keep their own mark in Room. A
        // group with none (first pull, or a build before schema 29) lists them whole once.
        val reactionsSince = pullSince(before?.reactionsSyncedAtEpochMillis)
        val reactionsAsked = since == null || withReactions
        val reactions = if (reactionsAsked && (since == null || reactionsSince == null)) firestore.reactionDocs(groupId) else null
        val newerReactions = if (reactionsAsked && since != null && reactionsSince != null) firestore.newerReactionDocs(groupId, reactionsSince) else null

        // Read after the network, in one write transaction with every write of this merge: a save and
        // its outbox row land wholly before or wholly after, so a change saved meanwhile is never undone.
        val merged = writes.run {
            val local = dao.group(groupId)
            val pending = pendingIn(groupId)
            // A row changed here and not yet uploaded keeps this phone's values: the server's copy is
            // the older one until the outbox sends it, so a pull in between must not undo the edit.
            val whole = "$KIND_GROUP:$groupId" in pending
            fun kept(field: String) = local?.takeIf { whole || "$KIND_GROUP:${groupFieldRow(groupId, field)}" in pending }
            val uid = uid()
            val group = SharedGroupEntity(
                id = groupId,
                name = kept("name")?.name ?: fields.string("name") ?: local?.name ?: AppText.copy.shared.group,
                type = kept("type")?.type ?: fields.string("type")?.let(::knownGroupType) ?: local?.type ?: GroupType.Other.name,
                icon = kept("icon")?.icon ?: fields.string("icon") ?: local?.icon ?: "group",
                simplifyDebts = kept("simplifyDebts")?.simplifyDebts ?: fields["simplifyDebts"] as? Boolean ?: true,
                createdAtEpochMillis = fields.long("createdAt") ?: local?.createdAtEpochMillis ?: now(),
                // A group brought back after signing in again finds "you" by the name this account claimed.
                meMemberId = meMemberId ?: local?.meMemberId ?: uid?.let { members?.let { docs -> claimedBy(docs, it) } },
                remote = true,
                creatorUid = fields.string("creatorUid"),
                inviteCode = fields.string("inviteCode"),
                inviteActive = fields.bool("inviteActive"),
                // Moves only in markSynced, once the log is in: a pull that fails before then starts here again.
                lastSyncedAtEpochMillis = pulledUpTo,
                // This phone's own mark, never on the server: a pull must not reset it.
                lastSeenAtEpochMillis = local?.lastSeenAtEpochMillis,
                reactionsSyncedAtEpochMillis = local?.reactionsSyncedAtEpochMillis,
                currency = currency,
                // #97: an end this phone set and has not sent yet stays as it is.
                endedOn = if ("$KIND_TRIP_END:$groupId" in pending) local?.endedOn else fields.string("endedOn"),
            )
            // Every write wakes the snapshot and the screens that read it, so an unchanged row is not written.
            if (group != local) dao.upsertGroup(group)
            val namesChanged = members != null && run {
                val pickable = pickableNames(dao.membersOf(groupId))
                storeMembers(groupId, members, payIds, playOffs, pending)
                moveOwnPromptPay(groupId, group.meMemberId, members, pending)
                pickableNames(dao.membersOf(groupId)) != pickable
            }
            // #48: read with the key the group's money names, so a baht amount an older build wrote into a
            // kyat group is skipped rather than taken as kyat.
            val batch = LogBatch()
            log.entries.forEach { doc -> batch.add(groupId, doc, WireAmount.key(currency)) }
            if (batch.isNotEmpty()) dao.receiveLog(batch.versions, batch.payments, batch.removals)
            val mine = pending.filter { it.startsWith("$KIND_REACTION:") }.map { it.removePrefix("$KIND_REACTION:") }.toSet()
            reactions?.let { docs -> dao.replaceReactions(groupId, docs.mapNotNull { reactionEntity(groupId, it) }, mine) }
            newerReactions?.takeIf { it.isNotEmpty() }?.let { docs ->
                val changed = docs.mapNotNull { reactionEntity(groupId, it) }.filterNot { it.id in mine }
                val gone = docs.mapNotNull { it.takenBackReaction() }.filterNot { it in mine }
                dao.receiveReactions(changed, gone)
            }
            log.repeats.forEach { doc -> receiveRepeat(groupId, doc) }
            Merged(namesChanged)
        }
        // The network only after the transaction: it holds Room's writer while it is open.
        if (merged.namesChanged) publishNames(groupId)
        // An empty answer leaves the mark: the next pull asks from the same place and costs the same.
        val gotNew = log.entries.isNotEmpty() || log.repeats.isNotEmpty()
        // Only the server's clock moves a mark: this phone's may run ahead and skip entries for good. The
        // log's read time came before the reactions query, so the reactions mark can only be early.
        val readTime = log.readTimeEpochMillis
        if (readTime != null && (since == null || gotNew)) dao.markSynced(groupId, readTime)
        if (readTime != null && reactionsAsked) dao.markReactionsSynced(groupId, readTime)
        seenGroupDocs[groupId] = fields
        if (members != null) membersListedAt[groupId] = now()
        return PullResult.Kept
    }

    /**
     * Names change rarely, so they are listed again only when the group document changed (a join or
     * a leave writes it), now and then, or when the log names someone this phone does not know.
     */
    private suspend fun membersDue(groupId: String, fields: Fields, local: SharedGroupEntity?, meMemberId: String?, log: PulledLog): Boolean {
        if (local == null || meMemberId != null || local.meMemberId == null || log.repeats.isNotEmpty()) return true
        if (seenGroupDocs[groupId] != fields) return true
        val listedAt = membersListedAt[groupId] ?: return true
        if (now() - listedAt >= MEMBERS_EVERY.inWholeMilliseconds) return true
        val named = log.entries.flatMap { it.memberIds() }
        return named.isNotEmpty() && !dao.membersOf(groupId).map { it.id }.toSet().containsAll(named)
    }

    private suspend fun storeMembers(groupId: String, members: List<FirestoreDoc>, payIds: Map<String, Fields>, playOffs: Map<String, Fields>, pending: Set<String>) {
        val known = dao.membersOf(groupId).associateBy { it.id }
        val pulled = members.filterNot { "$KIND_MEMBER:${it.id}" in pending }.map { doc ->
            val holder = doc.fields.string("claimedByUid")
            GroupMemberEntity(
                id = doc.id,
                groupId = groupId,
                name = doc.fields.string("name") ?: known[doc.id]?.name ?: AppText.copy.shared.someone,
                personId = known[doc.id]?.personId,
                archived = doc.fields.bool("archived"),
                createdAtEpochMillis = doc.fields.long("createdAt") ?: known[doc.id]?.createdAtEpochMillis ?: now(),
                claimedByUid = holder,
                // #199: an ID counts only on a claimed name. M5: the members-only copy when it was stored by
                // the name's holder; otherwise the field builds before M5 write on the name.
                promptPayId = holder?.let {
                    (payIds[doc.id]?.takeIf { pay -> pay.string("claimedByUid") == holder }?.string("promptPay") ?: doc.fields.string("promptPay"))
                        ?.let(PromptPay::normalize)
                },
                // D5: an opt-out counts only from the name's own holder; this phone's own waits to be sent.
                playOff = if ("$KIND_PLAY:${doc.id}" in pending) {
                    known[doc.id]?.playOff == true
                } else {
                    holder != null && playOffs[doc.id]?.takeIf { it.string("claimedByUid") == holder }?.bool("awardsOff") == true
                },
            )
        }
        val changed = pulled.filter { it != known[it.id] }
        if (changed.isNotEmpty()) dao.upsertMembers(changed)
    }

    /** M5: an ID this account left on its own name with an older build moves to the members-only place on the next upload. */
    private suspend fun moveOwnPromptPay(groupId: String, me: String?, members: List<FirestoreDoc>, pending: Set<String>) {
        val own = members.firstOrNull { it.id == me } ?: return
        if (own.fields.string("promptPay") == null || own.fields.string("claimedByUid") != uid() || "$KIND_MEMBER:${own.id}" in pending) return
        dao.queue(listOf(SyncOutboxEntity("$KIND_MEMBER:${own.id}", groupId, KIND_MEMBER, own.id, now())))
    }

    private class PulledLog(val entries: List<FirestoreDoc>, val repeats: List<FirestoreDoc>, val readTimeEpochMillis: Long?)

    /** A group's first pull on this phone: everything, also documents written before [SYNCED_AT] existed. */
    private suspend fun wholeLog(groupId: String): PulledLog {
        // The server's clock, from a query that matches nothing, read before the lists: an entry that
        // lands while they load is newer than it, so the next pull takes it.
        val readTime = firestore.changedSince("groups/$groupId", "entries", SYNCED_AT, FAR_FUTURE_MILLIS).readTimeEpochMillis
        val entries = firestore.list("groups/$groupId/entries")
        val repeats = firestore.list("groups/$groupId/repeats")
        return PulledLog(entries, repeats, readTime)
    }

    /** Later pulls: only what the server stamped after [since]. The log only grows, so the rest is here. */
    private suspend fun newerLog(groupId: String, since: Long): PulledLog {
        val entries = firestore.changedSince("groups/$groupId", "entries", SYNCED_AT, since)
        val repeats = firestore.changedSince("groups/$groupId", "repeats", SYNCED_AT, since)
        return PulledLog(entries.docs, repeats.docs, listOfNotNull(entries.readTimeEpochMillis, repeats.readTimeEpochMillis).minOrNull())
    }

    /** A repeat another member set up or changed. This phone's progress and reminder stay its own. */
    private suspend fun receiveRepeat(groupId: String, doc: FirestoreDoc) {
        val local = dao.repeat(doc.id)
        val rule = repeatEntity(groupId, doc, local, now()) ?: return
        // A kind, date, split or rate this build cannot read is not taken: stored, it would stop every launch.
        val domain = rule.toDomain() ?: return
        if (domain.amountMinor !in 0..MAX_MINOR_UNITS) return
        // Other money with no rate cannot become the group's, and would fail on each member's phone when due.
        if (domain.rate == null && domain.currency != dao.group(groupId)?.currency) return
        // The payer and every weight must be people in this group, or the repeat is not taken.
        val members = dao.membersOf(groupId).map { it.id }.toSet()
        if (domain.payerMemberId !in members || !members.containsAll(domain.weights.keys)) return
        val today = clock.now().toLocalDateTime(BangkokTime).date
        // Checked last, right before the write, so an edit saved during this pull is not undone.
        if (dao.queued("$KIND_REPEAT:${doc.id}") > 0) return
        val lastAdded = local?.lastAdded ?: domain.restoredLastAdded(dao.newestDateAfter(domain.entryIdPrefix)?.toLocalDateOrNull(), today)?.toString()
        dao.upsertRepeat(rule.copy(lastAdded = lastAdded))
    }

    /**
     * The group's document is not on the server, so no pull can ever fill it. It goes from this phone,
     * unless a change still waits: a group being shared for the first time has not reached the server yet.
     */
    private suspend fun goneFromServer(groupId: String): PullResult = writes.run {
        if (pendingIn(groupId).isNotEmpty()) return@run PullResult.Kept
        dao.forgetGroup(groupId)
        PullResult.Removed
    }

    /** One transaction, so a save landing meanwhile is kept as lost rather than deleted with no record. */
    private suspend fun forgetKeepingUnsent(groupId: String): PullResult = writes.run {
        dao.keepUnsent(groupId, now(), uid())
        dao.forgetGroup(groupId)
        PullResult.Removed
    }

    /** Outbox ids ("repeat:<id>", "member:<id>"…) for [groupId]: local changes not uploaded yet. */
    private suspend fun pendingIn(groupId: String): Set<String> = dao.outbox().filter { it.groupId == groupId }.map { it.id }.toSet()

    private fun now(): Long = clock.now().toEpochMilliseconds()
}

/** #96: the members' opt-outs. A refusal reads as none, so a pull from before the rules allowed them goes on. */
internal suspend fun Firestore.playDocs(groupId: String): List<FirestoreDoc> = try {
    list("groups/$groupId/play")
} catch (refused: FirestoreException) {
    if (refused.status != FirestoreStatus.FORBIDDEN) throw refused
    emptyList()
}

/** #95: every reaction in the group, or none where the rules do not allow them yet. */
internal suspend fun Firestore.reactionDocs(groupId: String): List<FirestoreDoc>? = try {
    list("groups/$groupId/reactions")
} catch (refused: FirestoreException) {
    if (refused.status != FirestoreStatus.FORBIDDEN) throw refused
    null
}

/** #95: the ids of [uid]'s reactions in [groupId], as the server holds them, so a leave can take them along. */
internal suspend fun Firestore.reactionIdsOf(groupId: String, uid: String): List<String> =
    reactionDocs(groupId).orEmpty().filter { it.fields.string("authorUid") == uid }.map { it.id }

/** #95: reactions the server stamped after [since]; none where the rules do not allow them yet. */
internal suspend fun Firestore.newerReactionDocs(groupId: String, since: Long): List<FirestoreDoc>? = try {
    changedSince("groups/$groupId", "reactions", SYNCED_AT, since).docs
} catch (refused: FirestoreException) {
    if (refused.status != FirestoreStatus.FORBIDDEN) throw refused
    null
}

/** M5: every PromptPay ID stored in [groupId] where only members read; none while the rules have no such place. */
internal suspend fun Firestore.promptPayDocs(groupId: String): List<FirestoreDoc> = try {
    list("groups/$groupId/pay")
} catch (refused: FirestoreException) {
    if (refused.status != FirestoreStatus.FORBIDDEN) throw refused
    emptyList()
}

/** 2100-01-01: a time no entry carries, so a query for later ones costs one read and answers with the server's clock. */
private const val FAR_FUTURE_MILLIS = 4_102_444_800_000L

/** How often a group's names are listed again when nothing else says they changed. */
private val MEMBERS_EVERY = 10.minutes
