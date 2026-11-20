package com.ptut.expense.domain.shared

import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.datetime.LocalDate

/** One row of a group's feed (p27), newest first. [isNew]: another member wrote it after the seen mark. */
sealed interface GroupFeedItem {
    val occurredAtEpochMillis: Long
    val isNew: Boolean

    /** [part] is what the expense means for me, in the group's money. */
    data class Expense(val expense: SharedExpense, val part: MyPart, override val isNew: Boolean) : GroupFeedItem {
        override val occurredAtEpochMillis: Long get() = expense.occurredAtEpochMillis
    }

    data class Payment(val payment: SharedPayment, override val isNew: Boolean) : GroupFeedItem {
        override val occurredAtEpochMillis: Long get() = payment.occurredAtEpochMillis
    }
}

/** The feed's rows of one day in Bangkok. */
data class GroupFeedDay(val date: LocalDate, val items: List<GroupFeedItem>)

/** A repeat still adding entries, with its [amount] in the group's money. */
data class RunningRepeat(val rule: RepeatRule, val amount: Satang)

/** #137: one group as its screen shows it, worked out from the log on [today]. */
data class GroupDetail(
    val group: SharedGroup,
    val today: LocalDate,
    /** Mine: above zero I am owed. */
    val net: Satang,
    /** Each member's net in the group. */
    val balances: Map<String, Satang>,
    /** Nobody owes anybody. */
    val everyoneSquare: Boolean,
    /** p27's spend: every current expense in the group's money. */
    val totalSpend: Satang,
    /** Expenses and payments together, newest first, by day. */
    val days: List<GroupFeedDay>,
    /** #63: the largest debt that involves me, or null when I have nothing to settle. */
    val settleUp: Transfer?,
    val repeats: List<RunningRepeat>,
    /** People on this phone who could be added: not archived and not in the group yet. */
    val addable: List<Person>,
    /** #190: the members who have joined, see [SharedGroup.hasJoined]. */
    val joined: Set<String>,
    /** The group's maker, or anyone in a group that is only on this phone or has no maker recorded. */
    val isCreator: Boolean,
    /** #218: only the creator removes a name from a shared group. */
    val canRemovePeople: Boolean,
    val removal: GroupRemoval,
)

/** #137: the group [groupId] as its screen shows it today; null once it has gone from this phone. [seen] is the mark the visit opened on. */
class GetGroupDetailUseCase(private val homeToday: GetHomeTodayUseCase) {
    operator fun invoke(snapshot: SharedSnapshot, groupId: String, seen: Long?): GroupDetail? = snapshot.groupDetail(groupId, seen, homeToday())
}

/** [GetGroupDetailUseCase] on a given [today]. */
fun SharedSnapshot.groupDetail(groupId: String, seen: Long?, today: LocalDate): GroupDetail? {
    val group = group(groupId) ?: return null
    val balances = nets(group.id)
    val createdHere = group.createdHere()
    return GroupDetail(
        group = group,
        today = today,
        net = myNet(group.id),
        balances = balances,
        everyoneSquare = balances.values.all { it == Satang.Zero },
        totalSpend = totalSpend(group.id),
        days = feed(group, seen),
        settleUp = whoPaysWhom(group.id).largestInvolving(group.meMemberId),
        repeats = repeats.filter { it.groupId == group.id && it.running(today) }.map { rule ->
            RunningRepeat(rule, convert(rule.amountMinor, Currencies.of(rule.currency), rule.rate, group.money))
        },
        addable = people.filter { person -> !person.archived && group.activeMembers.none { it.personId == person.id } },
        joined = group.activeMembers.filter { group.hasJoined(it.id) }.mapTo(mutableSetOf()) { it.id },
        isCreator = !group.remote || group.creatorUid == null || createdHere,
        canRemovePeople = !group.remote || createdHere,
        removal = group.removal(),
    )
}

private fun SharedSnapshot.feed(group: SharedGroup, seen: Long?): List<GroupFeedDay> {
    val expenses = currentExpenses(group.id).map { expense ->
        GroupFeedItem.Expense(expense, expense.partFor(group.meMemberId), group.isNewToMe(expense.authorMemberId, expense.createdAtEpochMillis, seen))
    }
    val payments = paymentsIn(group.id).map { payment ->
        GroupFeedItem.Payment(payment, group.isNewToMe(payment.authorMemberId, payment.createdAtEpochMillis, seen))
    }
    return (expenses + payments)
        .sortedByDescending { it.occurredAtEpochMillis }
        .groupBy { it.occurredAtEpochMillis.dateIn(HomeTime) }
        .map { (date, items) -> GroupFeedDay(date, items) }
}
