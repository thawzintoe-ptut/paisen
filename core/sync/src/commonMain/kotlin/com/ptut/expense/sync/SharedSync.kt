package com.ptut.expense.sync

import com.ptut.expense.data.local.dao.SharedDao
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.shared.SharedRepository
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.auth.Account
import com.ptut.expense.sync.auth.AuthException
import com.ptut.expense.sync.auth.AuthRepository
import com.ptut.expense.sync.auth.authTimeOf
import com.ptut.expense.sync.push.PushNotices
import com.ptut.expense.sync.push.PushRegistrar
import com.ptut.expense.sync.push.PushSender
import kotlinx.coroutines.CancellationException
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
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
 * [code] is registered when a name is picked (L4); [member]: the account is already in, so it
 * registers nothing (M1).
 */
data class JoinChoice(
    val groupId: String,
    val groupName: String,
    val names: List<Pair<String, String>>,
    val mine: Pair<String, String>? = null,
    val code: String? = null,
    val member: Boolean = false,
)

class JoinException(message: String) : Exception(message)

/** Sign-out stopped: [count] changes have not reached their groups, and leaving now would lose them. */
class ChangesWaiting(val count: Int) : Exception("$count changes waiting to sync")

/**
 * What one sync did. [groupsChanged]: a group came onto or went off this phone, so its repeat
 * reminders can be rebuilt (#203). [failed]: a group, or the sign-in behind every call, could not be reached.
 */
data class SyncOutcome(val groupsChanged: Boolean, val failed: Boolean)

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
    private val endedHere = MutableStateFlow(false)
    private val running = Mutex()

    // In memory only, read and written under [running]: a new process checks everything once.
    private var accountCheckedAt: Pair<String, Long>? = null

    private val puller = GroupPuller(firestore, dao, clock, ::publishNames) { (auth.account.value as? Account.SignedIn)?.uid }
    private val pusher = OutboxPusher(firestore, dao, repository, clock, pushes, failed, ::account, ::newInvite, ::publishNames)

    fun status(groupId: String): Flow<GroupSyncStatus> =
        combine(repository.snapshot, dao.observeOutbox(), failed, pusher.refused, auth.account) { snapshot, outbox, failed, refused, account ->
            val group = snapshot.group(groupId)
            val waiting = outbox.count { it.groupId == groupId }
            health(remote = group?.remote == true, account is Account.SignedIn, groupId in failed, waiting, notTaken = refused[groupId] ?: 0)
        }

    /** #196: the same, across every shared group, for the Shared tab's one line. */
    fun overallStatus(): Flow<GroupSyncStatus> =
        combine(repository.snapshot, dao.observeOutbox(), failed, pusher.refused, auth.account) { snapshot, outbox, failed, refused, account ->
            val remote = snapshot.groups.filter { it.remote }.map { it.id }.toSet()
            val waiting = outbox.count { it.groupId in remote }
            val notTaken = refused.filterKeys { it in remote }.values.sum()
            health(remote = remote.isNotEmpty(), account is Account.SignedIn, failed.any { it in remote }, waiting, notTaken)
        }

    private fun health(remote: Boolean, signedIn: Boolean, failed: Boolean, waiting: Int, notTaken: Int): GroupSyncStatus = when {
        !remote -> GroupSyncStatus(SyncHealth.Local, 0)
        !signedIn -> GroupSyncStatus(SyncHealth.SignedOut, waiting, notTaken)
        failed -> GroupSyncStatus(SyncHealth.Offline, waiting, notTaken)
        waiting > 0 -> GroupSyncStatus(SyncHealth.Waiting, waiting, notTaken)
        else -> GroupSyncStatus(SyncHealth.UpToDate, 0, notTaken)
    }

    /** The group screen has said it once; the count starts again from zero. */
    fun acknowledgeRefused(groupId: String) = pusher.acknowledgeRefused(groupId)

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
    suspend fun syncAll(): Boolean = syncAllOutcome().groupsChanged

    /** [syncAll], also saying whether it failed, so a poll can wait longer before the next. */
    suspend fun syncAllOutcome(): SyncOutcome = synced { account ->
        // The session check and the restore query cost reads on every poll; once in a while is enough.
        val mine = if (accountCheckDue(account.uid)) {
            if (!holdsSession(account.uid)) return@synced SyncOutcome(groupsChanged = endHere(), failed = false)
            memberships(account.uid)?.also { accountCheckedAt = account.uid to now() }
        } else {
            null
        }
        val docs = mine.orEmpty().associateBy { it.id }
        val tried = mine?.let { restore(it, docs) }.orEmpty()
        val restored = tried.filterValues { it }.keys
        var changed = restored.isNotEmpty()
        var anyFailed = false in tried.values
        (dao.remoteGroupIds() - restored).forEach { groupId ->
            val pulled = attempt(groupId) { if (puller.pull(groupId, docs[groupId]) == PullResult.Removed) changed = true }
            if (!pulled) anyFailed = true
        }
        SyncOutcome(changed, anyFailed)
    }

    /**
     * The group screen's poll: uploads what waits and pulls [groupId] alone. True, as for [syncAll],
     * when the group went off this phone.
     */
    suspend fun syncGroup(groupId: String): Boolean = syncGroupOutcome(groupId).groupsChanged

    /** [syncGroup], also saying whether it failed, so a poll can wait longer before the next. */
    suspend fun syncGroupOutcome(groupId: String): SyncOutcome = synced {
        var removed = false
        var pulled = true
        if (groupId in dao.remoteGroupIds()) pulled = attempt(groupId) { if (puller.pull(groupId) == PullResult.Removed) removed = true }
        SyncOutcome(removed, failed = !pulled)
    }

    private suspend fun synced(work: suspend (Account.SignedIn) -> SyncOutcome): SyncOutcome {
        if (!available || auth.account.value !is Account.SignedIn) return QUIET
        return running.withLock {
            // Read again inside: a sign-out or account deletion may have finished while this waited.
            val account = auth.account.value as? Account.SignedIn ?: return@withLock QUIET
            try {
                forgetOtherAccounts(account.uid)
                pusher.push()
                work(account)
            } catch (noToken: AuthException) {
                // #218: no group says "Up to date" when the sign-in behind every call has failed.
                failAll()
                SyncOutcome(groupsChanged = false, failed = true)
            }
        }
    }

    private fun accountCheckDue(uid: String): Boolean {
        val (checkedUid, at) = accountCheckedAt ?: return true
        return checkedUid != uid || now() - at >= ACCOUNT_CHECK_EVERY.inWholeMilliseconds
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
                .collect { runSuspendCatching { uploadQueued() } }
        }
    }

    private suspend fun uploadQueued() {
        val account = auth.account.value as? Account.SignedIn ?: return
        try {
            running.withLock {
                forgetOtherAccounts(account.uid)
                pusher.push()
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
            pusher.push()
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
        val group = requireNotNull(dao.groupOf(groupId)) { "No group $groupId" }
        running.withLock {
            if (!group.remote) {
                group.meMemberId?.let { dao.claimMember(it, account.name.firstWord(), account.uid) }
                firestore.create(
                    "groups",
                    groupId,
                    // #48: the money travels only here. A patch carrying it would change a key on a group made
                    // before #48, which the rules refuse, so every later edit of that group would be a 403.
                    group.fields() + mapOf("currency" to group.currency, "creatorUid" to account.uid, "memberUids" to listOf(account.uid), "inviteCode" to null, "inviteActive" to false),
                )
                val local = requireNotNull(dao.group(groupId))
                dao.upsertGroup(local.copy(remote = true, creatorUid = account.uid))
                pusher.queueWholeGroup(groupId)
                // Nobody else is in it yet, so its first upload tells no one.
                pusher.push(quietFor = groupId)
            }
        }
        return group.inviteCode?.takeIf { group.inviteActive && stillOpen(it) } ?: newInvite(groupId)
    }

    /** Whether the invite [code] still lets people in; offline, the code this phone holds is kept. */
    private suspend fun stillOpen(code: String): Boolean = try {
        invite(code)?.let(::open) == true
    } catch (expired: JoinException) {
        false
    } catch (unknown: FirestoreException) {
        true
    }

    /** The invite's fields, or null when no invite has [code]. One past its end date cannot be read, and says so. */
    private suspend fun invite(code: String): Fields? = try {
        firestore.get("invites/$code")?.fields
    } catch (refused: FirestoreException) {
        if (refused.status == FirestoreStatus.FORBIDDEN) throw JoinException(EXPIRED) else throw refused
    }

    private fun open(invite: Fields): Boolean = invite.bool("active") && invite.instant("expiresAt")?.let { it <= clock.now() } != true

    /** A fresh code; the old one stops working (#190). Only the creator may do this. */
    suspend fun newInvite(groupId: String): String {
        val account = signedIn()
        val group = requireNotNull(dao.group(groupId)) { "No group $groupId" }
        // Fails closed: a new code is never made while the old one may still be live.
        group.inviteCode?.let { old -> firestore.patch("invites/$old", OFF) }
        val code = InviteLinks.newCode()
        val expiresAt = clock.now() + INVITE_LIFETIME
        val names = pickableNames(dao.membersOf(groupId))
        firestore.create(
            "invites",
            code,
            mapOf("groupId" to groupId, "groupName" to group.name, "active" to true, "creatorUid" to account.uid, "expiresAt" to expiresAt, INVITE_NAMES to names),
        )
        firestore.patch("groups/$groupId", mapOf("inviteCode" to code, "inviteActive" to true))
        dao.setInvite(groupId, code, true)
        return code
    }

    /** "This invite has expired" from now on, for anyone who still has the link. */
    suspend fun switchOffInvite(groupId: String) {
        signedIn()
        val code = dao.group(groupId)?.inviteCode ?: return
        firestore.patch("invites/$code", OFF)
        firestore.patch("groups/$groupId", mapOf("inviteActive" to false))
        dao.setInvite(groupId, code, false)
    }

    /**
     * Step one of joining: check the code and list the names to choose from. L4: an invite that
     * carries its names is all that is read, so opening a link writes nothing. One made by a build
     * before L4 carries none, and the join is registered first to read them, as those builds do.
     */
    suspend fun openJoin(code: String): JoinChoice {
        val account = signedIn()
        // #218: a mistyped code is not an expired one, and says so.
        val invite = invite(code) ?: throw JoinException(AppText.copy.shared.sync.noSuchCode)
        if (!open(invite)) throw JoinException(EXPIRED)
        val groupId = invite.string("groupId") ?: throw JoinException(EXPIRED)
        val groupName = invite.string("groupName") ?: AppText.copy.shared.sync.aGroup
        // Readable to an account already in, or one that registered this code before; M1: a member registers nothing.
        val group = readableGroup(groupId)
        if (group == null) {
            invitedNames(invite)?.let { names -> return JoinChoice(groupId, groupName, names, code = code) }
            register(groupId, code)
        }
        val members = firestore.list("groups/$groupId/members")
        val alreadyMine = members.firstOrNull { it.fields.string("claimedByUid") == account.uid }
        return JoinChoice(
            groupId = groupId,
            groupName = groupName,
            names = if (alreadyMine != null) {
                emptyList()
            } else {
                members
                    .filter { it.fields.string("claimedByUid") == null && !it.fields.bool("archived") }
                    .map { it.id to (it.fields.string("name") ?: AppText.copy.shared.someone) }
            },
            mine = alreadyMine?.let { it.id to (it.fields.string("name") ?: account.name) },
            code = code,
            member = account.uid in group?.list("memberUids").orEmpty(),
        )
    }

    /** The group's fields when this account may read them, or null when the rules say no. */
    private suspend fun readableGroup(groupId: String): Fields? = try {
        firestore.get("groups/$groupId")?.fields
    } catch (refused: FirestoreException) {
        if (refused.status != FirestoreStatus.FORBIDDEN) throw refused
        null
    }

    /** Registers this account's join with [code], which lets it read the names; refused, the code has stopped. */
    private suspend fun register(groupId: String, code: String) {
        val uid = signedIn().uid
        val join = mapOf("code" to code)
        try {
            if (!firestore.create("groups/$groupId/joins", uid, join)) firestore.patch("groups/$groupId/joins/$uid", join)
        } catch (refused: FirestoreException) {
            if (refused.status == FirestoreStatus.FORBIDDEN) throw JoinException(EXPIRED) else throw refused
        }
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
        // L4: the join is registered once a name is picked, never when the link opened. Any member can
        // write the invite's copy of the names, so the name is checked against the group's own first.
        if (choice.code != null && !choice.member) {
            register(groupId, choice.code)
            if (memberId != null && !stillAsShown(choice, memberId)) throw JoinException(AppText.copy.shared.sync.nameTaken)
        }
        try {
            firestore.commit(listOf(claim, firestore.appendWrite("groups/$groupId", "memberUids", listOf(account.uid))))
        } catch (refused: FirestoreException) {
            if (refused.status != FirestoreStatus.FORBIDDEN) throw refused
            throw JoinException(if (memberId != null && claimedSince(groupId, memberId)) AppText.copy.shared.sync.nameTaken else EXPIRED)
        }
        running.withLock { puller.pull(groupId, meMemberId = mine) }
        val name = choice.mine?.second ?: choice.names.firstOrNull { it.first == memberId }?.second ?: account.name.firstWord()
        pushes?.tell(groupId, PushNotices(choice.groupName, name).joined())
        return groupId
    }

    /** The picked name is still nobody's and still called what the picker showed. */
    private suspend fun stillAsShown(choice: JoinChoice, memberId: String): Boolean {
        val name = try {
            firestore.get("groups/${choice.groupId}/members/$memberId")?.fields
        } catch (refused: FirestoreException) {
            if (refused.status == FirestoreStatus.FORBIDDEN) throw JoinException(EXPIRED) else throw refused
        } ?: return false
        val shown = choice.names.firstOrNull { it.first == memberId }?.second
        return name.string("claimedByUid") == null && !name.bool("archived") && name.string("name") == shown
    }

    /** Step two was refused: someone claimed [memberId] after step one, rather than the invite going off. */
    private suspend fun claimedSince(groupId: String, memberId: String): Boolean = try {
        firestore.get("groups/$groupId/members/$memberId")?.fields?.string("claimedByUid") != null
    } catch (unreadable: FirestoreException) {
        false
    }

    /** L4: the live invite lists the names nobody holds, so the next person to open it picks without writing. */
    private suspend fun publishNames(groupId: String) {
        val group = dao.group(groupId) ?: return
        val code = group.inviteCode?.takeIf { group.inviteActive } ?: return
        // A copy for convenience: a refused or lost write waits for the next change of names. Read first,
        // so an invite switched off or past its end date gets none, and the same names are not sent twice.
        runSuspendCatching {
            val invite = invite(code)?.takeIf(::open) ?: return@runSuspendCatching
            val names = pickableNames(dao.membersOf(groupId))
            if (invite.string(INVITE_NAMES) != names) firestore.patch("invites/$code", mapOf(INVITE_NAMES to names))
        }
    }

    /**
     * The creator removes a shared group for everyone: the invite stops working and the group is
     * marked deleted, so each member's phone drops it on its next pull. Its names and log can no
     * longer be read; the documents stay until the clean-up removes them.
     */
    suspend fun deleteForEveryone(groupId: String) {
        account()
        val group = requireNotNull(dao.group(groupId)) { "No group $groupId" }
        // Before the write: the Worker sends nothing for a group already deleted.
        pusher.noticesFor(groupId)?.deleted()?.let { pushes?.tellFirst(groupId, it) }
        // H3: every PromptPay ID goes in the same commit, and the server's time dates the clean-up.
        val withPromptPay = firestore.list("groups/$groupId/members").filter { it.fields.string("promptPay") != null }
        val stored = firestore.promptPayDocs(groupId)
        // D5 and #95: the opt-outs and this account's own reactions go too; the others' stay for the clean-up.
        val playOffs = firestore.playDocs(groupId)
        val reactions = firestore.reactionIdsOf(groupId, account().uid)
        firestore.commit(
            listOfNotNull(group.inviteCode?.let { firestore.updateWrite("invites/$it", OFF) }) +
                firestore.stampedUpdateWrite("groups/$groupId", mapOf("deleted" to true, "inviteActive" to false), DELETED_AT) +
                withPromptPay.map { firestore.updateWrite("groups/$groupId/members/${it.id}", mapOf("promptPay" to null)) } +
                stored.map { firestore.deleteWrite("groups/$groupId/pay/${it.id}") } +
                playOffs.map { firestore.deleteWrite("groups/$groupId/play/${it.id}") } +
                reactions.map { firestore.deleteWrite("groups/$groupId/reactions/$it") },
        )
        running.withLock { dao.forgetGroup(groupId) }
    }

    /**
     * A member who did not create the group leaves it: their name is free again, the others keep it.
     * What waits is sent first; unless [dropUnsent], leaving stops rather than lose it (#218).
     */
    suspend fun leaveGroup(groupId: String, dropUnsent: Boolean = false) {
        val account = account()
        if (!dropUnsent) {
            val waiting = running.withLock {
                pusher.push()
                dao.outbox().count { it.groupId == groupId }
            }
            if (waiting > 0) throw ChangesWaiting(waiting)
        }
        val me = dao.group(groupId)?.meMemberId
        pusher.noticesFor(groupId)?.left()?.let { pushes?.tellFirst(groupId, it) }
        firestore.commit(firestore.leaveWrites(groupId, me, account.uid, firestore.reactionIdsOf(groupId, account.uid)))
        running.withLock { dao.forgetGroup(groupId) }
    }

    /**
     * #187 and Apple 5.1.1(v): leave every group, then remove the account and this phone's copy.
     * Under the sync lock, so no sync stores this phone's sign-in again halfway through.
     */
    suspend fun deleteAccount() = running.withLock {
        // What waits goes first, as on sign-out: after the account is gone nothing can send it.
        pusher.push()
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
        val account = account()
        dao.remoteGroupIds().forEach { groupId ->
            val group = dao.group(groupId)
            // L2: nobody else can switch this creator's invite off, so it goes off as they leave for good.
            val created = group?.creatorUid == account.uid
            try {
                val reactions = firestore.reactionIdsOf(groupId, account.uid)
                pusher.noticesFor(groupId)?.left()?.let { pushes?.tellFirst(groupId, it) }
                firestore.commit(firestore.leaveWrites(groupId, group?.meMemberId, account.uid, reactions, closeInvite = created))
            } catch (refused: FirestoreException) {
                // Not a member any more (left elsewhere, or taken out by the creator): nothing to leave.
                if (refused.status != FirestoreStatus.FORBIDDEN) throw refused
            }
            // The group's switch already refuses every join; this makes the link say "expired" at once.
            if (created) group?.inviteCode?.let { code -> runSuspendCatching { firestore.patch("invites/$code", OFF) } }
        }
        runSuspendCatching { firestore.delete("sessions/${account.uid}") }
    }

    /** #187: shared groups leave this phone with the account. Your own spending stays. */
    private suspend fun forgetSharedGroups() {
        dao.remoteGroupIds().forEach { dao.forgetGroup(it) }
        failed.value = emptySet()
        puller.forget()
        // Signing in again brings the groups back at once rather than at the next check.
        accountCheckedAt = null
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
     * phone, or after a reinstall (#187). Each one tried, and whether it was brought back.
     */
    private suspend fun restore(mine: List<FirestoreDoc>, docs: Map<String, FirestoreDoc>): Map<String, Boolean> =
        groupsToRestore(mine, dao.remoteGroupIds().toSet()).associateWith { groupId -> attempt(groupId) { puller.pull(groupId, docs[groupId]) } }

    /** Every group document the account is in, or null when the query failed. */
    private suspend fun memberships(uid: String): List<FirestoreDoc>? = try {
        firestore.whereContains("groups", "memberUids", uid)
    } catch (unknown: FirestoreException) {
        null
    }

    private suspend fun waitingToSend(): Int {
        val remote = dao.remoteGroupIds().toSet()
        return dao.outbox().count { it.groupId in remote }
    }

    /**
     * Runs [work] for a group and remembers whether it reached Firestore, for the status line. Any
     * failure but a lost sign-in stays with this group, so the next one still syncs.
     */
    private suspend fun attempt(groupId: String, work: suspend () -> Unit): Boolean = try {
        work()
        failed.update { it - groupId }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (noToken: AuthException) {
        throw noToken
    } catch (failure: Exception) {
        failed.update { it + groupId }
        false
    }

    /** For sharing and joining, whose screens say "sign in to share". */
    private fun signedIn(): Account.SignedIn = auth.account.value as? Account.SignedIn ?: throw JoinException(AppText.copy.shared.sync.signInToShare)

    /** For everything else: a sign-out midway fails the sync as a lost session does, not as a join. */
    private fun account(): Account.SignedIn = auth.account.value as? Account.SignedIn ?: throw AuthException("Not signed in", sessionEnded = true)

    private fun now(): Long = clock.now().toEpochMilliseconds()
}

private val EXPIRED: String get() = AppText.copy.shared.sync.inviteExpired

/** What a sync that never started says: nothing changed, nothing failed. */
private val QUIET = SyncOutcome(groupsChanged = false, failed = false)

/** How long a new invite lets people in; old invites, made with no end date, never stop on their own. */
private val INVITE_LIFETIME = 90.days

/** An invite switched off, with its names emptied so an old link shows nobody's (L4). */
private val OFF: Fields = mapOf("active" to false, INVITE_NAMES to "")

/** The server's time on a group deleted for everyone, which the clean-up reads. */
private const val DELETED_AT = "deletedAt"

/** How often [SharedSync.syncAll] checks the account's session and looks for groups to bring back. */
private val ACCOUNT_CHECK_EVERY = 10.minutes

/** Quiet time after the last queued change, so one save's several rows go up in one push. */
private val UPLOAD_SOON = 1.seconds

internal fun String.firstWord(): String = trim().substringBefore(' ').ifEmpty { this }
