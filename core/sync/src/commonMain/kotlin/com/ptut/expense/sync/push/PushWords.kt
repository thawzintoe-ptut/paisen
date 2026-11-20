package com.ptut.expense.sync.push

import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.Member
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
        /** Null without a group id. A notice missing a field its words need is read as an old build's. */
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
 * M6: words a push on the phone that got it, in [copy]'s language. When this phone does not hold
 * what the notice names yet, it syncs first, for at most [wait] in all; past that it words what it
 * has, and a name it lacks reads "Someone".
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
        val actor = before?.group(push.groupId)?.holderName(push.by) ?: group?.holderName(push.by)
        val title = push.title?.takeIf { it.isNotBlank() } ?: group?.name.orEmpty()
        return PushWords(title, pushSentence(notice, group?.members.orEmpty(), after, actor, push.title, copy, group?.moneyFormat()))
    }

    private companion object {
        val SYNC_WAIT = 8.seconds
        val SETTLE = 1.seconds
    }
}

/**
 * The sentence for [notice] from what this phone holds: names from [members], the description
 * from the stored expense version or repeat, [actor] null for "Someone".
 */
fun pushSentence(
    notice: PushNotice,
    members: List<Member>,
    snapshot: SharedSnapshot,
    actor: String?,
    groupName: String?,
    copy: SharedCopy,
    money: HomeMoney?,
): String {
    val push = copy.push
    val who = actor ?: copy.someone
    fun name(memberId: String?) = members.firstOrNull { it.id == memberId }?.name ?: copy.someone
    fun expense() = snapshot.expenseVersions.firstOrNull { it.versionId == notice.about }?.description.orIf(push.anExpense)
    fun repeat() = snapshot.repeats.firstOrNull { it.id == notice.about }?.description.orIf(push.anExpense)
    val format = notice.currency?.let { Currencies.of(it).toHomeMoney() } ?: money ?: Home.money
    val amount = notice.amount?.let { Satang.ofMinorUnits(it).formatCompact(format) }.orEmpty()
    return when (notice.kind) {
        PushKind.ExpenseAdded -> push.added(who, expense(), amount)
        PushKind.ExpenseEdited -> push.edited(who, expense())
        PushKind.ExpenseRemoved -> push.removed(who, expense())
        PushKind.ExpensePutBack -> push.putBack(who, expense())
        PushKind.RepeatAdded -> push.repeatAdded(expense(), amount)
        PushKind.Paid -> push.paid(name(notice.from), name(notice.to), amount)
        PushKind.PaymentRemoved -> push.paymentRemoved(who)
        PushKind.PaymentPutBack -> push.paymentPutBack(who)
        PushKind.RepeatSetUp -> push.repeatSetUp(who, repeat())
        PushKind.RepeatChanged -> push.repeatChanged(who, repeat())
        PushKind.RepeatStopped -> push.repeatStopped(who, repeat())
        PushKind.PersonAdded -> push.addedPerson(who, name(notice.about))
        PushKind.PersonRemoved -> push.removedPerson(who, name(notice.about))
        PushKind.NameChanged -> push.changedName(who)
        PushKind.Renamed -> push.renamed(who, groupName?.takeIf { it.isNotBlank() } ?: copy.group)
        PushKind.Joined -> push.joined(who)
        PushKind.Left -> push.left(who)
        PushKind.Deleted -> push.deleted(who)
    }
}

/** Whether this phone already holds everything [push]'s words name. */
internal fun SharedSnapshot.holds(push: ReceivedPush): Boolean {
    val notice = push.notice ?: return true
    val group = group(push.groupId) ?: return false
    val subject = when (notice.kind) {
        in EXPENSE_KINDS -> expenseVersions.any { it.versionId == notice.about }
        in REPEAT_KINDS -> repeats.any { it.id == notice.about }
        PushKind.Paid -> group.member(notice.from.orEmpty()) != null && group.member(notice.to.orEmpty()) != null
        PushKind.PersonAdded, PushKind.PersonRemoved -> group.member(notice.about.orEmpty()) != null
        else -> true
    }
    val actor = notice.kind in NO_ACTOR || group.holderName(push.by) != null
    return subject && actor
}

/** A notice the words need more of, such as an addition with no amount, is not worded at all. */
internal fun PushNotice.complete(): Boolean = when (kind) {
    PushKind.ExpenseAdded, PushKind.RepeatAdded -> about != null && amount != null
    PushKind.Paid -> from != null && to != null && amount != null
    in EXPENSE_KINDS, in REPEAT_KINDS, PushKind.PersonAdded, PushKind.PersonRemoved -> about != null
    else -> true
}

/** The name of the member [uid] claimed in this group. */
private fun SharedGroup.holderName(uid: String?): String? = uid?.let { holder -> members.firstOrNull { it.claimedByUid == holder }?.name }

private fun String?.orIf(fallback: String): String = this?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

private val EXPENSE_KINDS = setOf(PushKind.ExpenseAdded, PushKind.ExpenseEdited, PushKind.ExpenseRemoved, PushKind.ExpensePutBack, PushKind.RepeatAdded)
private val REPEAT_KINDS = setOf(PushKind.RepeatSetUp, PushKind.RepeatChanged, PushKind.RepeatStopped)
private val NO_ACTOR = setOf(PushKind.RepeatAdded, PushKind.Paid)
