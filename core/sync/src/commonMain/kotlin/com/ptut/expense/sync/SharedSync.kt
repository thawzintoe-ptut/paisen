package com.ptut.expense.sync

import com.ptut.expense.data.local.dao.SharedDao
import com.ptut.expense.data.local.entity.ExpenseShareEntity
import com.ptut.expense.data.local.entity.ExpenseVersionEntity
import com.ptut.expense.data.local.entity.GroupMemberEntity
import com.ptut.expense.data.local.entity.PaymentRemovalEntity
import com.ptut.expense.data.local.entity.RepeatRuleEntity
import com.ptut.expense.data.local.entity.SharedGroupEntity
import com.ptut.expense.data.local.entity.SharedPaymentEntity
import com.ptut.expense.data.local.entity.SyncOutboxEntity
import com.ptut.expense.data.repository.END_NEVER
import com.ptut.expense.data.repository.KIND_GROUP
import com.ptut.expense.data.repository.KIND_MEMBER
import com.ptut.expense.data.repository.KIND_PAYMENT
import com.ptut.expense.data.repository.KIND_PAYMENT_REMOVAL
import com.ptut.expense.data.repository.KIND_REPEAT
import com.ptut.expense.data.repository.KIND_VERSION
import com.ptut.expense.data.repository.toDomain
import com.ptut.expense.data.repository.toEntity
import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.PaymentRemoval
import com.ptut.expense.domain.shared.PromptPay
import com.ptut.expense.domain.shared.RepeatRule
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedPayment
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.domain.shared.firstLastAdded
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.auth.Account
import com.ptut.expense.sync.auth.AuthException
import com.ptut.expense.sync.auth.AuthRepository
import com.ptut.expense.sync.auth.authTimeOf
import com.ptut.expense.sync.push.PushNotice
import com.ptut.expense.sync.push.PushNotices
import com.ptut.expense.sync.push.PushRegistrar
import com.ptut.expense.sync.push.PushSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** #189: what the group screen says about sync. [SignedOut]: a shared group on a phone whose session ended (#218). */
enum class SyncHealth { Local, UpToDate, Waiting, Offline, SignedOut }

/** [refused] counts changes the group would not take (#194); they have left the outbox. */
data class GroupSyncStatus(val health: SyncHealth, val waiting: Int, val refused: Int = 0)

/**
 * The join screen's two steps (#190): the group, and the names nobody has claimed yet. [mine] is
 * set instead when this account already holds a name there, so it is not offered a second one.
 */
data class JoinChoice(
    val groupId: String,
    val groupName: String,
    val names: List<Pair<String, String>>,
    val mine: Pair<String, String>? = null,
)

class JoinException(message: String) : Exception(message)

/** Sign-out stopped: [count] changes have not reached their groups, and leaving now would lose them. */
class ChangesWaiting(val count: Int) : Exception("$count changes waiting to sync")

/**
 * #189 and #190: keeps each shared group's log the same on every member's phone.
 *
 * Writes land in Room first and wait in the outbox; [syncAll] uploads them and pulls what other
 * members added. The log only grows, so pulling is "insert what is not here yet", by id, and every
 * phone orders it the same way (#133). Your own transactions and the links to them never sync.
 */
class SharedSync internal constructor(
    private val auth: AuthRepository,
    private val firestore: Firestore,
    private val repository: SharedRepository,
    private val dao: SharedDao,
    private val clock: Clock = Clock.System,
    /** Null in a build with no push Worker: no notice is built or sent. */
    private val pushes: PushSender? = null,
    private val registrar: PushRegistrar? = null,
) {
    val available: Boolean get() = auth.available

    /** Whether this phone is signed in, so the invite button knows to ask first (#187). */
    val signedIn: Flow<Boolean> get() = auth.account.map { it is Account.SignedIn }.distinctUntilChanged()

    /** The same, right now, so a screen starts from the truth rather than from "signed out". */
    val isSignedIn: Boolean get() = auth.account.value is Account.SignedIn

    private val failed = MutableStateFlow<Set<String>>(emptySet())
    private val refused = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val endedHere = MutableStateFlow(false)
    private val running = Mutex()

    fun status(groupId: String): Flow<GroupSyncStatus> =
        combine(repository.snapshot, dao.observeOutbox(), failed, refused, auth.account) { snapshot, outbox, failed, refused, account ->
            val group = snapshot.group(groupId)
            val waiting = outbox.count { it.groupId == groupId }
            val notTaken = refused[groupId] ?: 0
            when {
                group == null || !group.remote -> GroupSyncStatus(SyncHealth.Local, 0)
                account !is Account.SignedIn -> GroupSyncStatus(SyncHealth.SignedOut, waiting, notTaken)
                groupId in failed -> GroupSyncStatus(SyncHealth.Offline, waiting, notTaken)
                waiting > 0 -> GroupSyncStatus(SyncHealth.Waiting, waiting, notTaken)
                else -> GroupSyncStatus(SyncHealth.UpToDate, 0, notTaken)
            }
        }

    /** #196: the same, across every shared group, for the Shared tab's one line. */
    fun overallStatus(): Flow<GroupSyncStatus> =
        combine(repository.snapshot, dao.observeOutbox(), failed, refused, auth.account) { snapshot, outbox, failed, refused, account ->
            val remote = snapshot.groups.filter { it.remote }.map { it.id }.toSet()
            val waiting = outbox.count { it.groupId in remote }
            val notTaken = refused.filterKeys { it in remote }.values.sum()
            when {
                remote.isEmpty() -> GroupSyncStatus(SyncHealth.Local, 0)
                account !is Account.SignedIn -> GroupSyncStatus(SyncHealth.SignedOut, waiting, notTaken)
                failed.any { it in remote } -> GroupSyncStatus(SyncHealth.Offline, waiting, notTaken)
                waiting > 0 -> GroupSyncStatus(SyncHealth.Waiting, waiting, notTaken)
                else -> GroupSyncStatus(SyncHealth.UpToDate, 0, notTaken)
            }
        }

    /** The group screen has said it once; the count starts again from zero. */
    fun acknowledgeRefused(groupId: String) = refused.update { it - groupId }

    /** True once a newer sign-in on another phone signed this one out, until the notice is read. */
    val signedOutElsewhere: StateFlow<Boolean> get() = endedHere

    fun acknowledgeSignedOutElsewhere() {
        endedHere.value = false
    }

    /**
     * Uploads what waits, then pulls every shared group the account is in, also ones this phone
     * does not hold yet. Quiet when signed out or not set up. True when a group came onto or went
     * off this phone, so its repeat reminders can be rebuilt (#203).
     */
    suspend fun syncAll(): Boolean {
        if (!available || auth.account.value !is Account.SignedIn) return false
        return running.withLock {
            // Read again inside: a sign-out or account deletion may have finished while this waited.
            val account = auth.account.value as? Account.SignedIn ?: return@withLock false
            try {
                forgetOtherAccounts(account.uid)
                push()
                if (!holdsSession(account.uid)) return@withLock endHere()
                val restored = restore(account.uid)
                var changed = restored.isNotEmpty()
                (dao.remoteGroupIds() - restored).forEach { groupId -> attempt(groupId) { if (!pull(groupId)) changed = true } }
                changed
            } catch (noToken: AuthException) {
                // #218: no group says "Up to date" when the sign-in behind every call has failed.
                failAll()
                false
            }
        }
    }

    /**
     * #220: what a save queues goes up within seconds rather than at the next poll. Started once
     * for the app; a change that cannot go now waits for the next sync, as before.
     */
    @OptIn(FlowPreview::class)
    fun uploadSoonAfterSaves(scope: CoroutineScope) {
        if (!available) return
        scope.launch {
            dao.observeOutbox()
                .map { rows -> rows.map { it.id } }
                .distinctUntilChanged()
                .debounce(UPLOAD_SOON)
                .filter { it.isNotEmpty() }
                // As the polling does: one failed upload must not stop every later one.
                .collect { runCatching { uploadQueued() } }
        }
    }

    private suspend fun uploadQueued() {
        val account = auth.account.value as? Account.SignedIn ?: return
        try {
            running.withLock {
                forgetOtherAccounts(account.uid)
                push()
            }
        } catch (noToken: AuthException) {
            failAll()
        }
    }

    /**
     * A session that died keeps its shared groups on the phone (#195), for the same account to sync
     * again. Another account signing in instead must not send them as its own, so they go first.
     */
    private suspend fun forgetOtherAccounts(uid: String) {
        dao.remoteGroupIds().forEach { groupId ->
            val me = dao.group(groupId)?.meMemberId ?: return@forEach
            val holder = dao.membersOf(groupId).firstOrNull { it.id == me }?.claimedByUid
            if (holder != null && holder != uid) dao.forgetGroup(groupId)
        }
    }

    private suspend fun failAll() {
        failed.value = dao.remoteGroupIds().toSet()
    }

    /**
     * #187: signing out takes shared groups off this phone; they come back when the account signs in
     * again. What still waits is sent first, and sign-out stops rather than lose it.
     */
    suspend fun signOut() {
        running.withLock {
            push()
            val waiting = waitingToSend()
            if (waiting > 0) throw ChangesWaiting(waiting)
            forgetSharedGroups()
        }
        registrar?.forgetDevice()
        auth.signOut()
    }

    /**
     * #190: a group goes from this phone to Firestore the first time someone invites to it. This
     * phone's member takes the account's name and claims it. Returns the invite code.
     */
    suspend fun share(groupId: String): String {
        val account = signedIn()
        val group = requireNotNull(groupOf(groupId)) { "No group $groupId" }
        running.withLock {
            if (!group.remote) {
                group.meMemberId?.let { dao.claimMember(it, account.name.firstWord(), account.uid) }
                firestore.create(
                    "groups",
                    groupId,
                    group.fields() + mapOf("creatorUid" to account.uid, "memberUids" to listOf(account.uid), "inviteCode" to null, "inviteActive" to false),
                )
                val local = requireNotNull(dao.group(groupId))
                dao.upsertGroup(local.copy(remote = true, creatorUid = account.uid))
                queueWholeGroup(groupId)
                // Nobody else is in it yet, so its first upload tells no one.
                push(quietFor = groupId)
            }
        }
        return group.inviteCode?.takeIf { group.inviteActive } ?: newInvite(groupId)
    }

    /** A fresh code; the old one stops working (#190). Only the creator may do this. */
    suspend fun newInvite(groupId: String): String {
        val account = signedIn()
        val group = requireNotNull(dao.group(groupId)) { "No group $groupId" }
        // Fails closed: a new code is never made while the old one may still be live.
        group.inviteCode?.let { old -> firestore.patch("invites/$old", mapOf("active" to false)) }
        val code = InviteLinks.newCode()
        firestore.create("invites", code, mapOf("groupId" to groupId, "groupName" to group.name, "active" to true, "creatorUid" to account.uid))
        firestore.patch("groups/$groupId", mapOf("inviteCode" to code, "inviteActive" to true))
        dao.setInvite(groupId, code, true)
        return code
    }

    /** "This invite has expired" from now on, for anyone who still has the link. */
    suspend fun switchOffInvite(groupId: String) {
        signedIn()
        val code = dao.group(groupId)?.inviteCode ?: return
        firestore.patch("invites/$code", mapOf("active" to false))
        firestore.patch("groups/$groupId", mapOf("inviteActive" to false))
        dao.setInvite(groupId, code, false)
    }

    /** Step one of joining: check the code, register the join, list the names to choose from. */
    suspend fun openJoin(code: String): JoinChoice {
        val account = signedIn()
        // #218: a mistyped code is not an expired one, and says so.
        val invite = firestore.get("invites/$code")?.fields ?: throw JoinException(AppText.copy.shared.sync.noSuchCode)
        if (!invite.bool("active")) throw JoinException(EXPIRED)
        val groupId = invite.string("groupId") ?: throw JoinException(EXPIRED)
        val join = mapOf("code" to code, "at" to now())
        try {
            if (!firestore.create("groups/$groupId/joins", account.uid, join)) firestore.patch("groups/$groupId/joins/${account.uid}", join)
        } catch (refused: FirestoreException) {
            if (refused.status == FORBIDDEN) throw JoinException(EXPIRED) else throw refused
        }
        val members = firestore.list("groups/$groupId/members")
        val alreadyMine = members.firstOrNull { it.fields.string("claimedByUid") == account.uid }
        return JoinChoice(
            groupId = groupId,
            groupName = invite.string("groupName") ?: AppText.copy.shared.sync.aGroup,
            names = if (alreadyMine != null) {
                emptyList()
            } else {
                members
                    .filter { it.fields.string("claimedByUid") == null && !it.fields.bool("archived") }
                    .map { it.id to (it.fields.string("name") ?: AppText.copy.shared.someone) }
            },
            mine = alreadyMine?.let { it.id to (it.fields.string("name") ?: account.name) },
        )
    }

    /** Step two: claim [memberId], or add the account's own name when it is not on the list. */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun join(choice: JoinChoice, memberId: String?): String {
        val account = signedIn()
        val groupId = choice.groupId
        val mine = memberId ?: Uuid.random().toString()
        val claim = if (memberId != null) {
            firestore.updateWrite("groups/$groupId/members/$mine", mapOf("claimedByUid" to account.uid))
        } else {
            firestore.updateWrite(
                "groups/$groupId/members/$mine",
                mapOf("name" to account.name.firstWord(), "claimedByUid" to account.uid, "archived" to false, "createdAt" to now()),
                create = true,
            )
        }
        try {
            firestore.commit(listOf(claim, firestore.appendWrite("groups/$groupId", "memberUids", listOf(account.uid))))
        } catch (refused: FirestoreException) {
            if (refused.status != FORBIDDEN) throw refused
            throw JoinException(if (memberId != null && claimedSince(groupId, memberId)) AppText.copy.shared.sync.nameTaken else EXPIRED)
        }
        running.withLock { pull(groupId, meMemberId = mine) }
        val name = choice.mine?.second ?: choice.names.firstOrNull { it.first == memberId }?.second ?: account.name.firstWord()
        pushes?.tell(groupId, PushNotices(choice.groupName, name).joined())
        return groupId
    }

    /** Step two was refused: someone claimed [memberId] after step one, rather than the invite going off. */
    private suspend fun claimedSince(groupId: String, memberId: String): Boolean = try {
        firestore.get("groups/$groupId/members/$memberId")?.fields?.string("claimedByUid") != null
    } catch (unreadable: FirestoreException) {
        false
    }

    /**
     * The creator removes a shared group for everyone: the invite stops working and the group is
     * marked deleted, so each member's phone drops it on its next pull. Firestore keeps the
     * documents (its rules delete nothing); nobody can open them from the app again.
     */
    suspend fun deleteForEveryone(groupId: String) {
        signedIn()
        val group = requireNotNull(dao.group(groupId)) { "No group $groupId" }
        // Before the write: the Worker sends nothing for a group already deleted.
        noticesFor(groupId)?.deleted()?.let { pushes?.tellFirst(groupId, it) }
        group.inviteCode?.let { firestore.patch("invites/$it", mapOf("active" to false)) }
        firestore.patch("groups/$groupId", mapOf("deleted" to true, "inviteActive" to false))
        running.withLock { dao.forgetGroup(groupId) }
    }

    /**
     * A member who did not create the group leaves it: their name is free again, the others keep it.
     * What waits is sent first; unless [dropUnsent], leaving stops rather than lose it (#218).
     */
    suspend fun leaveGroup(groupId: String, dropUnsent: Boolean = false) {
        val account = signedIn()
        if (!dropUnsent) {
            val waiting = running.withLock {
                push()
                dao.outbox().count { it.groupId == groupId }
            }
            if (waiting > 0) throw ChangesWaiting(waiting)
        }
        val me = dao.group(groupId)?.meMemberId
        noticesFor(groupId)?.left()?.let { pushes?.tellFirst(groupId, it) }
        firestore.commit(
            listOfNotNull(
                me?.let { firestore.updateWrite("groups/$groupId/members/$it", mapOf("claimedByUid" to null, "promptPay" to null)) },
                firestore.removeWrite("groups/$groupId", "memberUids", listOf(account.uid)),
            ),
        )
        running.withLock { dao.forgetGroup(groupId) }
    }

    /**
     * #187 and Apple 5.1.1(v): leave every group, then remove the account and this phone's copy.
     * Under the sync lock, so no sync stores this phone's sign-in again halfway through.
     */
    suspend fun deleteAccount() = running.withLock {
        // What waits goes first, as on sign-out: after the account is gone nothing can send it.
        push()
        val waiting = waitingToSend()
        if (waiting > 0) throw ChangesWaiting(waiting)
        leaveAll()
        registrar?.forgetDevice()
        auth.deleteAccount()
        forgetSharedGroups()
    }

    /**
     * Before the account is deleted: let go of every claim and leave every group. A group that
     * cannot be left stops the deletion, or the dead account would hold its name there for good.
     */
    private suspend fun leaveAll() {
        val account = signedIn()
        dao.remoteGroupIds().forEach { groupId ->
            val me = dao.group(groupId)?.meMemberId
            val writes = listOfNotNull(
                me?.let { firestore.updateWrite("groups/$groupId/members/$it", mapOf("claimedByUid" to null, "promptPay" to null)) },
                firestore.removeWrite("groups/$groupId", "memberUids", listOf(account.uid)),
            )
            try {
                noticesFor(groupId)?.left()?.let { pushes?.tellFirst(groupId, it) }
                firestore.commit(writes)
            } catch (refused: FirestoreException) {
                // Not a member any more (left elsewhere, or taken out by the creator): nothing to leave.
                if (refused.status != FORBIDDEN) throw refused
            }
        }
        runCatching { firestore.delete("sessions/${account.uid}") }
    }

    /** #187: shared groups leave this phone with the account. Your own spending stays. */
    private suspend fun forgetSharedGroups() {
        dao.remoteGroupIds().forEach { dao.forgetGroup(it) }
        failed.value = emptySet()
    }

    /**
     * One phone at a time: this phone stores its sign-in when it is the newest, and learns when
     * another phone's is newer. True while this phone holds the account, and when it cannot tell.
     */
    private suspend fun holdsSession(uid: String): Boolean {
        val mine = authTimeOf(auth.idToken()) ?: return true
        return try {
            when (sessionVerdict(firestore.get("sessions/$uid")?.fields?.long("signedInAt"), mine)) {
                SessionVerdict.Claim -> {
                    firestore.set("sessions/$uid", mapOf("signedInAt" to mine))
                    true
                }

                SessionVerdict.Keep -> true

                SessionVerdict.EndHere -> false
            }
        } catch (unknown: FirestoreException) {
            true
        }
    }

    /**
     * The account signed in on another phone later, so this one lets go of its shared groups and
     * signs out. Not while changes still wait: they go first, on a later sync.
     */
    private suspend fun endHere(): Boolean {
        if (waitingToSend() > 0) return false
        forgetSharedGroups()
        registrar?.forgetDevice()
        auth.signOut()
        endedHere.value = true
        return true
    }

    /**
     * Groups the account is in that this phone does not hold: after signing in again, on a new
     * phone, or after a reinstall (#187). Returns the ones brought back.
     */
    private suspend fun restore(uid: String): Set<String> {
        val mine = try {
            firestore.whereContains("groups", "memberUids", uid)
        } catch (unknown: FirestoreException) {
            return emptySet()
        }
        return groupsToRestore(mine, dao.remoteGroupIds().toSet()).filter { groupId -> attempt(groupId) { pull(groupId) } }.toSet()
    }

    private suspend fun waitingToSend(): Int {
        val remote = dao.remoteGroupIds().toSet()
        return dao.outbox().count { it.groupId in remote }
    }

    /**
     * #194: a change the server refuses for good (a 4xx other than an expired session, a timeout
     * or a rate limit) leaves the outbox and is counted once; anything that may pass later stays.
     * A row that has to wait holds back the rest of its group, so a group's changes go up in the
     * order they were made and no entry lands before the name that signs it.
     */
    private suspend fun push(quietFor: String? = null) {
        val held = mutableSetOf<String>()
        for (row in dao.outbox()) {
            if (row.groupId in held) continue
            try {
                val notice = upload(row, notify = row.groupId != quietFor)
                dao.dequeue(row.id)
                failed.update { it - row.groupId }
                notice?.let { pushes?.tell(row.groupId, it) }
            } catch (refusal: FirestoreException) {
                if (refusal.isPermanent()) {
                    dao.dequeue(row.id)
                    takeBack(row)
                    refused.update { it + (row.groupId to (it[row.groupId] ?: 0) + 1) }
                } else {
                    failed.update { it + row.groupId }
                    held += row.groupId
                }
            }
        }
    }

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
        // #199: only this phone's own member carries a PromptPay ID; nobody writes another's.
        val promptPay = if (own) mapOf("promptPay" to member.promptPayId) else emptyMap()
        val fields = mapOf("name" to member.name, "claimedByUid" to member.claimedByUid, "archived" to member.archived, "createdAt" to now()) + promptPay
        if (firestore.create("groups/${group.id}/members", member.id, fields)) return notices?.personAdded(member.name)?.takeUnless { own }
        val path = "groups/${group.id}/members/${member.id}"
        val creator = group.creatorUid == uid
        // Read first to word the notice (a PromptPay ID alone tells nobody), and to learn whose
        // account a name the creator removes was held by.
        val before = if (notices != null || (creator && member.archived)) firestore.get(path)?.fields else null
        firestore.patch(path, mapOf("name" to member.name))
        // Only the creator removes a name (#190), so no other phone sends one back.
        if (creator) archive(group.id, path, member.archived, holder = before?.string("claimedByUid")?.takeIf { it != uid })
        if (own) firestore.patch(path, promptPay)
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
     * Removing a name someone claimed also takes their account out of the group, after a new invite
     * replaces the live one, so they can no longer read it, write to it or come back on the old link.
     */
    private suspend fun archive(groupId: String, path: String, archived: Boolean, holder: String?) {
        if (!archived || holder == null) {
            firestore.patch(path, mapOf("archived" to archived))
            return
        }
        if (dao.group(groupId)?.inviteActive == true) newInvite(groupId)
        // One commit: the rules refuse a claimed name archived without its account taken out.
        firestore.commit(
            listOf(
                firestore.updateWrite(path, mapOf("archived" to true, "claimedByUid" to null, "promptPay" to null)),
                firestore.removeWrite("groups/$groupId", "memberUids", listOf(holder)),
            ),
        )
    }

    /** The notice for what went up, or null when it was already there, tells nobody, or [notify] is off. */
    private suspend fun upload(row: SyncOutboxEntity, notify: Boolean): PushNotice? {
        // Room, not the snapshot: a row saved a moment ago may not be in the snapshot yet, and a
        // null here takes it out of the outbox for good.
        val group = groupOf(row.groupId) ?: return null
        val uid = signedIn().uid
        // Null when nothing will be sent, so the reads that word a notice are skipped too.
        val notices = pushes?.takeIf { notify }?.let { noticesFor(group) }
        return when (row.kind) {
            KIND_GROUP -> {
                // Only a new name is news; an icon or the debt setting tells nobody.
                val before = notices?.let { firestore.get("groups/${group.id}")?.fields?.string("name") }
                firestore.patch("groups/${group.id}", group.fields())
                notices?.takeIf { before != null && before != group.name }?.renamed()
            }

            KIND_MEMBER -> group.member(row.rowId)?.let { member -> uploadMember(group, member, uid, notices) }

            // The server stamps each entry, so every phone pulls only what came after its last pull.
            KIND_VERSION -> dao.version(row.rowId)?.let { it.toDomain(dao.sharesOf(it.versionId)) }?.let { version ->
                val created = firestore.createStamped("groups/${group.id}/entries", version.versionId, version.fields(uid), SYNCED_AT)
                notices?.takeIf { created }?.expense(version, dao.versionsOf(version.id).mapNotNull { it.toDomain(emptyList()) })
            }

            KIND_PAYMENT -> dao.payment(row.rowId)?.toDomain()?.let { payment ->
                val created = firestore.createStamped("groups/${group.id}/entries", payment.id, payment.fields(uid), SYNCED_AT)
                notices?.takeIf { created }?.payment(payment)
            }

            // #218: a removal is a document of its own; the payment's document never changes.
            KIND_PAYMENT_REMOVAL -> dao.paymentRemoval(row.rowId)?.toDomain()?.let { removal ->
                val created = firestore.createStamped("groups/${group.id}/entries", removal.id, removal.fields(uid), SYNCED_AT)
                notices?.takeIf { created }?.paymentRemoval(removal)
            }

            // #182: the template only. Each phone keeps its own progress and reminder choice. The
            // creation time it sends is the stored one, which the rules require to stay put.
            KIND_REPEAT -> dao.repeat(row.rowId)?.toDomain()?.let { rule ->
                val path = "groups/${group.id}/repeats/${rule.id}"
                val isNew = notices?.let { firestore.get(path) == null }
                firestore.upsertStamped(path, rule.fields(uid), SYNCED_AT)
                isNew?.let { notices.repeat(rule, it) }
            }

            else -> null
        }
    }

    /** A group and its members as Room holds them now, which the snapshot may not show yet. */
    private suspend fun groupOf(groupId: String): SharedGroup? = dao.group(groupId)?.toDomain(dao.membersOf(groupId))

    private fun noticesFor(group: SharedGroup): PushNotices {
        // The name this phone's member has in the group, which is how the others see it.
        val actor = group.meMemberId?.let(group::member)?.name ?: signedIn().name.firstWord()
        return PushNotices(group.name, actor, group.members)
    }

    private fun noticesFor(groupId: String): PushNotices? = pushes?.let { repository.snapshot.value.group(groupId)?.let(::noticesFor) }

    /** Everything another member's phone has written, added to this one. [meMemberId] is set when joining. */

    /** False when the group was deleted for everyone, or this account was taken out of it, and it has gone from this phone. */
    private suspend fun pull(groupId: String, meMemberId: String? = null): Boolean {
        val remote = try {
            firestore.get("groups/$groupId")
        } catch (refused: FirestoreException) {
            // The creator took this account out of the group: nothing in it can be read here any more.
            if (refused.status != FORBIDDEN) throw refused
            dao.forgetGroup(groupId)
            return false
        } ?: return true
        val local = dao.group(groupId)
        val fields = remote.fields
        // The creator deleted it for everyone: it goes from this phone too. Your own spending stays.
        if (fields.bool("deleted")) {
            dao.forgetGroup(groupId)
            return false
        }
        // A row changed here and not yet uploaded keeps this phone's values: the server's copy is
        // the older one until the outbox sends it, so a pull in between must not undo the edit.
        val kept = local?.takeIf { "$KIND_GROUP:$groupId" in pendingIn(groupId) }
        val members = firestore.list("groups/$groupId/members")
        val uid = (auth.account.value as? Account.SignedIn)?.uid
        dao.upsertGroup(
            SharedGroupEntity(
                id = groupId,
                name = kept?.name ?: fields.string("name") ?: local?.name ?: "Group",
                type = kept?.type ?: fields.string("type")?.let(::knownGroupType) ?: local?.type ?: GroupType.Other.name,
                icon = kept?.icon ?: fields.string("icon") ?: local?.icon ?: "group",
                simplifyDebts = kept?.simplifyDebts ?: fields["simplifyDebts"] as? Boolean ?: true,
                createdAtEpochMillis = fields.long("createdAt") ?: local?.createdAtEpochMillis ?: now(),
                // A group brought back after signing in again finds "you" by the name this account claimed.
                meMemberId = meMemberId ?: local?.meMemberId ?: uid?.let { claimedBy(members, it) },
                remote = true,
                creatorUid = fields.string("creatorUid"),
                inviteCode = fields.string("inviteCode"),
                inviteActive = fields.bool("inviteActive"),
                // Moves only in markSynced, once the log is in: a pull that fails before then starts here again.
                lastSyncedAtEpochMillis = local?.lastSyncedAtEpochMillis,
                // This phone's own mark, never on the server: a pull must not reset it.
                lastSeenAtEpochMillis = local?.lastSeenAtEpochMillis,
            ),
        )
        val known = dao.membersOf(groupId).associateBy { it.id }
        val membersPending = pendingIn(groupId)
        dao.upsertMembers(
            members.filterNot { "$KIND_MEMBER:${it.id}" in membersPending }.map { doc ->
                GroupMemberEntity(
                    id = doc.id,
                    groupId = groupId,
                    name = doc.fields.string("name") ?: known[doc.id]?.name ?: "Someone",
                    personId = known[doc.id]?.personId,
                    archived = doc.fields.bool("archived"),
                    createdAtEpochMillis = doc.fields.long("createdAt") ?: known[doc.id]?.createdAtEpochMillis ?: now(),
                    claimedByUid = doc.fields.string("claimedByUid"),
                    // #199: an ID counts only on a claimed name; one on an unclaimed name is ignored.
                    promptPayId = doc.fields.string("promptPay")?.let(PromptPay::normalize)?.takeIf { doc.fields.string("claimedByUid") != null },
                )
            },
        )
        val since = pullSince(local?.lastSyncedAtEpochMillis)
        val log = if (since == null) wholeLog(groupId) else newerLog(groupId, since)
        log.entries.forEach { doc -> receive(groupId, doc) }
        log.repeats.forEach { doc -> receiveRepeat(groupId, doc) }
        dao.markSynced(groupId, log.readTimeEpochMillis ?: now())
        return true
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
        val f = doc.fields
        val local = dao.repeat(doc.id)
        val rule = runCatching {
            RepeatRuleEntity(
                id = doc.id,
                groupId = groupId,
                kind = f.string("kind") ?: return,
                frequency = f.string("frequency") ?: return,
                startDate = f.string("start") ?: return,
                endKind = f.string("endKind") ?: END_NEVER,
                endDate = f.string("endDate"),
                endTimes = f.int("endTimes"),
                remind = local?.remind ?: true,
                active = f.bool("active"),
                lastAdded = local?.lastAdded,
                description = f.string("description").orEmpty(),
                categoryId = f.string("categoryId"),
                currency = f.string("currency") ?: return,
                amountMinor = f.long("amountMinor") ?: return,
                rateUnscaled = f.long("rateUnscaled"),
                rateScale = f.int("rateScale"),
                payerMemberId = f.string("payerMemberId") ?: return,
                splitMode = f.string("splitMode") ?: return,
                weights = f.string("weights").orEmpty(),
                createdAtEpochMillis = f.long("createdAt") ?: now(),
            ).also { it.toDomain() }
        }.getOrNull() ?: return
        // The payer and every weight must be people in this group, or the repeat is not taken.
        val members = dao.membersOf(groupId).map { it.id }.toSet()
        val domain = rule.toDomain()
        if (domain.payerMemberId !in members || !members.containsAll(domain.weights.keys)) return
        val today = clock.now().toLocalDateTime(BangkokTime).date
        // Checked last, right before the write, so an edit saved during this pull is not undone.
        if ("$KIND_REPEAT:${doc.id}" in pendingIn(groupId)) return
        dao.upsertRepeat(rule.copy(lastAdded = local?.lastAdded ?: domain.firstLastAdded(today)?.toString()))
    }

    /** Outbox ids ("repeat:<id>", "member:<id>"…) for [groupId]: local changes not uploaded yet. */
    private suspend fun pendingIn(groupId: String): Set<String> = dao.outbox().filter { it.groupId == groupId }.map { it.id }.toSet()

    private suspend fun receive(groupId: String, doc: FirestoreDoc) {
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
                    amountSatang = f.long("amountSatang") ?: return,
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
                        amountSatang = s.long("amountSatang") ?: 0L,
                    )
                }
                // A split mode or rate this build cannot read is not taken: stored, it would stop every launch.
                // Nor is a split whose parts don't add up to the total: the balances would gain money from it.
                if (version.toDomain(shares) != null && addsUp(version, shares)) dao.receiveVersion(version, shares)
            }

            KIND_PAYMENT -> {
                val payment = SharedPaymentEntity(
                    id = doc.id,
                    groupId = groupId,
                    fromMemberId = f.string("fromMemberId") ?: return,
                    toMemberId = f.string("toMemberId") ?: return,
                    amountSatang = f.long("amountSatang") ?: return,
                    method = f.string("method") ?: return,
                    occurredAtEpochMillis = f.long("occurredAt") ?: return,
                    authorMemberId = f.string("authorMemberId"),
                    createdAtEpochMillis = f.long("createdAt") ?: return,
                )
                val real = payment.amountSatang > 0 && payment.fromMemberId != payment.toMemberId
                if (real && payment.toDomain() != null) dao.insertPaymentIfNew(payment)
            }

            KIND_PAYMENT_REMOVAL -> dao.insertPaymentRemovalIfNew(
                PaymentRemovalEntity(
                    id = doc.id,
                    groupId = groupId,
                    paymentId = f.string("paymentId") ?: return,
                    removed = f.bool("removed"),
                    authorMemberId = f.string("authorMemberId"),
                    createdAtEpochMillis = f.long("createdAt") ?: return,
                ),
            )
        }
    }

    /** From Room, as [upload] reads them: what was saved just before Share may not be in the snapshot yet. */
    private suspend fun queueWholeGroup(groupId: String) {
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

    /** Runs [work] for a group and remembers whether it reached Firestore, for the status line. */
    private suspend fun attempt(groupId: String, work: suspend () -> Unit): Boolean = try {
        work()
        failed.update { it - groupId }
        true
    } catch (failure: FirestoreException) {
        failed.update { it + groupId }
        false
    }

    private fun signedIn(): Account.SignedIn = auth.account.value as? Account.SignedIn ?: throw JoinException(AppText.copy.shared.sync.signInToShare)

    private fun now(): Long = clock.now().toEpochMilliseconds()
}

private val EXPIRED: String get() = AppText.copy.shared.sync.inviteExpired

/** Firestore's answer when the rules refuse a write, here a code that is no longer live. */
private const val FORBIDDEN = 403

/** The field the server stamps on every entry and repeat with its commit time. */
private const val SYNCED_AT = "syncedAt"

/** 2100-01-01: a time no entry carries, so a query for later ones costs one read and answers with the server's clock. */
private const val FAR_FUTURE_MILLIS = 4_102_444_800_000L

/** Quiet time after the last queued change, so one save's several rows go up in one push. */
private val UPLOAD_SOON = 1.seconds

/** Statuses that can pass on a later try: an expired session, a timeout, a rate limit. */
private val RETRYABLE_4XX = setOf(401, 408, 429)

private fun FirestoreException.isPermanent(): Boolean = status in 400..499 && status !in RETRYABLE_4XX

private fun String.firstWord(): String = trim().substringBefore(' ').ifEmpty { this }

/** Every phone stores a split whose parts add up to its total, in its currency and in satang (#134). */
private fun addsUp(version: ExpenseVersionEntity, shares: List<ExpenseShareEntity>): Boolean =
    shares.isNotEmpty() &&
        version.amountMinor >= 0 &&
        shares.all { it.amountMinor >= 0 && it.amountSatang >= 0 } &&
        shares.sumOf { it.amountMinor } == version.amountMinor &&
        shares.sumOf { it.amountSatang } == version.amountSatang

/** A group type a newer app added is stored as Other, which every build can read. */
private fun knownGroupType(name: String): String = GroupType.entries.firstOrNull { it.name == name }?.name ?: GroupType.Other.name

private fun SharedGroup.fields(): Fields = mapOf(
    "name" to name,
    "type" to type.name,
    "icon" to icon,
    "simplifyDebts" to simplifyDebts,
    "createdAt" to createdAtEpochMillis,
)

private fun SharedExpense.fields(uid: String): Fields = mapOf(
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
    "amountSatang" to satang.toMinorUnits(),
    "payerMemberId" to payerMemberId,
    "splitMode" to splitMode.name,
    "authorMemberId" to authorMemberId,
    "removed" to removed,
    "cause" to cause,
    "shares" to shares.map {
        mapOf("memberId" to it.memberId, "weight" to it.weight, "amountMinor" to it.amountMinor, "amountSatang" to it.satang.toMinorUnits())
    },
)

private fun SharedPayment.fields(uid: String): Fields = mapOf(
    "kind" to KIND_PAYMENT,
    "authorUid" to uid,
    "createdAt" to createdAtEpochMillis,
    "fromMemberId" to fromMemberId,
    "toMemberId" to toMemberId,
    "amountSatang" to amount.toMinorUnits(),
    "method" to method.name,
    "occurredAt" to occurredAtEpochMillis,
    "authorMemberId" to authorMemberId,
)

private fun PaymentRemoval.fields(uid: String): Fields = mapOf(
    "kind" to KIND_PAYMENT_REMOVAL,
    "authorUid" to uid,
    "createdAt" to createdAtEpochMillis,
    "paymentId" to paymentId,
    "removed" to removed,
    "authorMemberId" to authorMemberId,
)

/** #182's template as Firestore holds it. Never this phone's progress or its reminder choice. */
private fun RepeatRule.fields(uid: String): Fields = toEntity().let { rule ->
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
