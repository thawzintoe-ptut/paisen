package com.ptut.expense.domain.shared

import com.ptut.expense.domain.Home
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.matchesQuery
import com.ptut.expense.domain.today.GetHomeTodayUseCase
import kotlinx.datetime.LocalDate

/** One group on the Shared tab (#136). */
data class GroupSummary(
    val group: SharedGroup,
    /** Mine: above zero I am owed. */
    val net: Satang,
    /** Everyone still in the group but me. */
    val others: List<Member>,
    /** The moneys the group has used, its own first (#48). */
    val currencies: List<String>,
    /** p26's "rent repeats 1st": the first repeat still running (#185). */
    val repeat: RepeatRule?,
    /** Research finding 5: who wrote each row since my last visit, other members only. */
    val newFrom: List<String>,
)

/** p26: the Shared tab, every figure derived from the log (p36). */
data class SharedHome(
    val groups: List<GroupSummary>,
    val people: List<PersonBalance>,
    /** One line per money, the home's first; one settled line in the home's money while nobody owes anybody (#48). */
    val overall: List<Overall>,
    /** The people a new group can take: archived ones join no new split. */
    val knownPeople: List<Person>,
)

/** #136: groups and people whose names match [query], and where I stand overall. */
class GetSharedHomeUseCase(private val homeToday: GetHomeTodayUseCase) {
    operator fun invoke(snapshot: SharedSnapshot, query: String): SharedHome {
        val today = homeToday()
        return SharedHome(
            groups = snapshot.groups.filter { it.name.matchesQuery(query) }.map { group ->
                GroupSummary(
                    group = group,
                    net = snapshot.myNet(group.id),
                    others = group.activeMembers.filterNot { group.isMe(it.id) },
                    currencies = snapshot.currenciesOf(group.id),
                    repeat = snapshot.repeats.firstOrNull { it.groupId == group.id && it.running(today) },
                    newFrom = snapshot.newFromOthers(group),
                )
            },
            people = snapshot.peopleBalances().filter { it.name.matchesQuery(query) },
            overall = snapshot.overall().ifEmpty { listOf(Overall(Home.money.code, Satang.Zero, Satang.Zero, Satang.Zero)) },
            knownPeople = snapshot.people.filterNot { it.archived },
        )
    }
}

/** A row of People (#141). */
sealed interface PersonSummary {
    val name: String
    val archived: Boolean

    /** One balance in one money, matched as [peopleBalances] does, and the names of the groups behind it. */
    data class InGroups(val balance: PersonBalance, val person: Person?, val groupNames: List<String>) : PersonSummary {
        override val name: String get() = balance.name
        override val archived: Boolean get() = person?.archived == true
    }

    /** #187: a person made in a group that was later forgotten still belongs in the list, settled. */
    data class InNoGroup(val person: Person) : PersonSummary {
        override val name: String get() = person.name
        override val archived: Boolean get() = person.archived
    }
}

data class PeopleOverview(
    /** Archived people last (#56). */
    val rows: List<PersonSummary>,
    /** The people a new split can take. */
    val knownPeople: List<Person>,
)

/** #141: everyone I split with whose name matches [query], one row per money their groups count in (#48). */
class GetPeopleBalancesUseCase {
    operator fun invoke(snapshot: SharedSnapshot, query: String): PeopleOverview {
        val byId = snapshot.people.associateBy { it.id }
        val balances = snapshot.peopleBalances()
        val inGroups = balances.map { balance ->
            PersonSummary.InGroups(balance, byId[balance.key], balance.groupIds.mapNotNull { snapshot.group(it)?.name }.distinct())
        }
        val alone = snapshot.people.filter { person -> balances.none { it.key == person.id } }.map { PersonSummary.InNoGroup(it) }
        return PeopleOverview(
            rows = (inGroups + alone).filter { it.name.matchesQuery(query) }.sortedBy { it.archived },
            knownPeople = snapshot.people.filterNot { it.archived },
        )
    }
}

/** #184: the payments that settle a group, and p30's note when it used more than its own money. */
data class GroupSettlement(
    val group: SharedGroup,
    val settlement: Settlement,
    /** The moneys other than the group's own that its expenses used. */
    val otherCurrencies: List<String>,
)

/** #135: who pays whom in [groupId]; null once the group has gone from this phone. */
class GetSettlementUseCase {
    operator fun invoke(snapshot: SharedSnapshot, groupId: String): GroupSettlement? {
        val group = snapshot.group(groupId) ?: return null
        return GroupSettlement(group, snapshot.whoPaysWhom(groupId), snapshot.currenciesOf(groupId).filter { it != group.currency })
    }
}

/** #139: what is owed between two members of a group, and where PromptPay fits (#199). */
data class PaymentBetween(
    val group: SharedGroup,
    /** What the payer owes the payee in the payments the group would settle with (#135). */
    val owed: Satang,
    /** The payee's PromptPay ID, only when I pay: a QR to pay myself is no use. PromptPay moves baht only (#48). */
    val payeePromptPay: String?,
    /** The money comes to me and something is still owed, so I may ask for it. */
    val canAskToPay: Boolean,
    /** My PromptPay ID, in full, for the ask, so they can pay from the chat. */
    val askPromptPay: String?,
)

/** #139: [fromMemberId] paying [toMemberId] in [groupId]; null once the group has gone from this phone. */
class GetPaymentBetweenUseCase {
    operator fun invoke(snapshot: SharedSnapshot, groupId: String, fromMemberId: String, toMemberId: String): PaymentBetween? {
        val group = snapshot.group(groupId) ?: return null
        val owed = snapshot.whoPaysWhom(groupId).owed(fromMemberId, toMemberId)
        val payee = group.member(toMemberId)
        val canAsk = group.isMe(toMemberId) && owed > Satang.Zero
        return PaymentBetween(
            group = group,
            owed = owed,
            payeePromptPay = payee?.promptPayId?.takeIf { group.isMe(fromMemberId) && group.currency == Currencies.Thb.code },
            canAskToPay = canAsk,
            askPromptPay = payee?.promptPayId?.takeIf { canAsk },
        )
    }
}

/** p32's filters. */
enum class ActivityFilter { All, Expenses, Payments, Edits }

/** One change on the timeline and the group it is in. [myEffect]: what an added or removed expense means for me, as [effectFor]. */
data class ActivityEntry(val event: SharedEvent, val group: SharedGroup, val myEffect: Satang)

/** The entries of one day in Bangkok. */
data class ActivityDay(val date: LocalDate, val entries: List<ActivityEntry>)

/** #140: one timeline of everything shared, newest first, on [today]. */
data class ActivityFeed(
    /** The group the timeline is about; null for every group, or once it has gone. */
    val group: SharedGroup?,
    val today: LocalDate,
    /** #64: "New since you last looked": others' changes after the mark, kept out of [days]. */
    val fresh: List<ActivityEntry>,
    val days: List<ActivityDay>,
)

/** #140: the changes in [groupId], or in every group when it is null, that [filter] keeps; [since] null makes nothing new. */
class GetActivityUseCase(private val homeToday: GetHomeTodayUseCase) {
    operator fun invoke(snapshot: SharedSnapshot, groupId: String?, filter: ActivityFilter, since: Long?): ActivityFeed {
        val entries = snapshot.events(groupId)
            .filter { it.kept(filter) }
            .mapNotNull { event -> snapshot.group(event.groupId)?.let { group -> ActivityEntry(event, group, event.myEffect(group.meMemberId)) } }
        val (fresh, earlier) = entries.partition { it.group.isNewToMe(it.event.authorMemberId, it.event.atEpochMillis, since) }
        return ActivityFeed(
            group = groupId?.let { snapshot.group(it) },
            today = homeToday(),
            fresh = fresh,
            days = earlier
                .groupBy { it.event.atEpochMillis.dateIn(HomeTime) }
                .map { (date, rows) -> ActivityDay(date, rows) },
        )
    }

    private fun SharedEvent.kept(filter: ActivityFilter): Boolean = when (filter) {
        ActivityFilter.All -> true
        ActivityFilter.Expenses -> this is SharedEvent.ExpenseAdded || this is SharedEvent.ExpenseRemoved
        ActivityFilter.Payments -> this is SharedEvent.PaymentMade || this is SharedEvent.PaymentRemovalChanged
        ActivityFilter.Edits -> this is SharedEvent.ExpenseEdited
    }

    private fun SharedEvent.myEffect(me: String?): Satang = when (this) {
        is SharedEvent.ExpenseAdded -> expense.effectFor(me)
        is SharedEvent.ExpenseRemoved -> last.effectFor(me)
        else -> Satang.Zero
    }
}
