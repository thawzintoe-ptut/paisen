package com.ptut.expense.sync.push

import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.moneyFormat
import com.ptut.expense.locale.SharedCopy
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A push as the Worker delivers it, from FCM data on Android or an iOS push's userInfo. [uid] is
 * the account it was sent to, [by] the sender's, [title] the group's stored name. [body] is an old
 * build's sentence, or the Worker's "1 new" for a structured [notice], which is null for the former.
 */
data class ReceivedPush(
    val groupId: String,
    val uid: String?,
    val title: String?,
    val body: String?,
    val notice: PushNotice?,
    val by: String?,
) {
    companion object {
        /** Null without a group id. A notice missing the id of the record it names is read as an old build's. */
        fun from(data: Map<String, String?>): ReceivedPush? {
            val groupId = data["groupId"]?.takeIf { it.isNotBlank() } ?: return null
            val notice = PushKind.ofWire(data["kind"])?.let { kind ->
                PushNotice(kind, data["about"], data["from"], data["to"], data["amount"]?.toLongOrNull(), data["currency"])
            }?.takeIf { it.complete() }
            return ReceivedPush(groupId, data["uid"], data["title"], data["body"], notice, data["by"])
        }
    }
}

/** What a notification shows. */
data class PushWords(val title: String, val body: String)

/**
 * M6: words a push on the phone that got it, in [copy]'s language, from records this phone holds:
 * never an amount, a name or a party the push itself carries. When the record the notice names is
 * not here yet, it syncs first, for at most [wait] in all. A record still missing, or one another
 * member wrote, gets the generic "1 new".
 */
class PushReader(
    private val snapshot: StateFlow<SharedSnapshot>,
    private val sync: suspend () -> Unit,
    private val wait: Duration = SYNC_WAIT,
) {
    suspend fun words(push: ReceivedPush, copy: SharedCopy): PushWords {
        val notice = push.notice ?: return PushWords(push.title.orEmpty(), push.body.orEmpty())
        var before: SharedSnapshot? = null
        withTimeoutOrNull(wait) {
            val held = snapshot.first { it !== SharedSnapshot.Empty }
            before = held
            if (!held.holds(push)) {
                runSuspendCatching { sync() }
                // Room answers the snapshot a moment after the sync writes it.
                withTimeoutOrNull(SETTLE) { snapshot.first { it.holds(push) } }
            }
        }
        val after = snapshot.value
        val group = after.group(push.groupId) ?: before?.group(push.groupId)
        // Leaving and deleting let the sender's claim go right after the push, so before the sync first.
        val sender = before?.group(push.groupId)?.claimedBy(push.by) ?: group?.claimedBy(push.by)
        val title = push.title?.takeIf { it.isNotBlank() } ?: group?.name.orEmpty()
        val sentence = group?.takeIf { after.wroteIt(notice, it, sender?.id) }
            ?.let { pushSentence(notice, it.members, after, sender?.name, push.title, copy, it.moneyFormat()) }
        return PushWords(title, sentence ?: copy.home.newSince(1))
    }

    private companion object {
        val SYNC_WAIT = 8.seconds
        val SETTLE = 1.seconds
    }
}

/**
 * The sentence for [notice] from the record it names in [snapshot]: the stored version, payment,
 * repeat or member, its amount in [money]. Names come from [members]; [actor] null reads
 * "Someone". Null when this phone does not hold that record.
 */
fun pushSentence(
    notice: PushNotice,
    members: List<Member>,
    snapshot: SharedSnapshot,
    actor: String?,
    groupName: String?,
    copy: SharedCopy,
    money: HomeMoney,
): String? {
    val push = copy.push
    val who = actor ?: copy.someone
    fun name(memberId: String) = members.firstOrNull { it.id == memberId }?.name ?: copy.someone
    fun version() = snapshot.expenseVersions.firstOrNull { it.versionId == notice.about }
    fun what(version: SharedExpense) = version.description.orIf(push.anExpense)
    fun repeat() = snapshot.repeats.firstOrNull { it.id == notice.about }?.description?.orIf(push.anExpense)
    fun person() = members.firstOrNull { it.id == notice.about }?.name
    return when (notice.kind) {
        PushKind.ExpenseAdded -> version()?.let { push.added(who, what(it), it.satang.formatCompact(money)) }

        PushKind.ExpenseEdited -> version()?.let { push.edited(who, what(it)) }

        PushKind.ExpenseRemoved -> version()?.let { push.removed(who, what(it)) }

        PushKind.ExpensePutBack -> version()?.let { push.putBack(who, what(it)) }

        PushKind.RepeatAdded -> version()?.let { push.repeatAdded(what(it), it.satang.formatCompact(money)) }

        PushKind.Paid -> snapshot.payments.firstOrNull { it.id == notice.about }
            ?.let { push.paid(name(it.fromMemberId), name(it.toMemberId), it.amount.formatCompact(money)) }

        PushKind.PaymentRemoved -> push.paymentRemoved(who)

        PushKind.PaymentPutBack -> push.paymentPutBack(who)

        PushKind.RepeatSetUp -> repeat()?.let { push.repeatSetUp(who, it) }

        PushKind.RepeatChanged -> repeat()?.let { push.repeatChanged(who, it) }

        PushKind.RepeatStopped -> repeat()?.let { push.repeatStopped(who, it) }

        PushKind.PersonAdded -> person()?.let { push.addedPerson(who, it) }

        PushKind.PersonRemoved -> person()?.let { push.removedPerson(who, it) }

        PushKind.NameChanged -> push.changedName(who)

        PushKind.Renamed -> push.renamed(who, groupName?.takeIf { it.isNotBlank() } ?: copy.group)

        PushKind.Joined -> push.joined(who)

        PushKind.Left -> push.left(who)

        PushKind.Deleted -> push.deleted(who)
    }
}

/** Whether this phone already holds the record [push] names, written by its sender, and the sender. */
internal fun SharedSnapshot.holds(push: ReceivedPush): Boolean {
    val notice = push.notice ?: return true
    val group = group(push.groupId) ?: return false
    val sender = group.claimedBy(push.by)
    return (notice.kind in NO_ACTOR || sender != null) && wroteIt(notice, group, sender?.id)
}

/**
 * Whether this phone holds the record [notice] names in [group] and, where that record names its
 * writer, [senderId] wrote it: the member the sending account claimed, never one the push names.
 */
internal fun SharedSnapshot.wroteIt(notice: PushNotice, group: SharedGroup, senderId: String?): Boolean {
    fun byTheSender(author: String?) = author == null || author == senderId
    val about = notice.about
    return when (notice.kind) {
        in EXPENSE_KINDS -> expenseVersions.any { it.versionId == about && it.groupId == group.id && byTheSender(it.authorMemberId) }

        PushKind.Paid -> payments.any { it.id == about && it.groupId == group.id && byTheSender(it.authorMemberId) }

        PushKind.PaymentRemoved, PushKind.PaymentPutBack -> paymentRemovals.any {
            it.paymentId == about && it.groupId == group.id && it.removed == (notice.kind == PushKind.PaymentRemoved) && byTheSender(it.authorMemberId)
        }

        in REPEAT_KINDS -> repeats.any { it.id == about && it.groupId == group.id }

        PushKind.PersonAdded, PushKind.PersonRemoved -> group.member(about.orEmpty()) != null

        else -> true
    }
}

/** Every kind that names a record needs its id; nothing else in a push is read. */
internal fun PushNotice.complete(): Boolean = kind in ACTOR_ONLY || about != null

/** The member [uid] claimed in this group. */
private fun SharedGroup.claimedBy(uid: String?): Member? = uid?.let { holder -> members.firstOrNull { it.claimedByUid == holder } }

private fun String.orIf(fallback: String): String = trim().takeIf { it.isNotEmpty() } ?: fallback

private val EXPENSE_KINDS = setOf(PushKind.ExpenseAdded, PushKind.ExpenseEdited, PushKind.ExpenseRemoved, PushKind.ExpensePutBack, PushKind.RepeatAdded)
private val REPEAT_KINDS = setOf(PushKind.RepeatSetUp, PushKind.RepeatChanged, PushKind.RepeatStopped)
private val NO_ACTOR = setOf(PushKind.RepeatAdded, PushKind.Paid)
private val ACTOR_ONLY = setOf(PushKind.NameChanged, PushKind.Renamed, PushKind.Joined, PushKind.Left, PushKind.Deleted)
