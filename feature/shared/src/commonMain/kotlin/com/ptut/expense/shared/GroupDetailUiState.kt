package com.ptut.expense.shared

import com.ptut.expense.domain.HomeMoney
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.shared.GroupRemoval
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.Person
import kotlinx.datetime.LocalDate

/** [joined] is false while a member is only a name nobody has claimed (#190). */
data class MemberChipUiModel(val id: String, val label: String, val isMe: Boolean, val joined: Boolean = true, val name: String = label)

/** One row of p27's feed: an expense, or a payment with its neutral tile. */
sealed interface GroupFeedRow {
    val id: String

    data class Expense(
        override val id: String,
        val icon: String,
        val title: String,
        val detail: String,
        val word: String,
        val amount: String,
        val tone: BalanceTone,
        /** Another member wrote it after the newest row shown last visit (research finding 5). */
        val isNew: Boolean = false,
    ) : GroupFeedRow

    data class Payment(
        override val id: String,
        val title: String,
        val detail: String,
        val amount: String,
        /** #218: for the payment's sheet. "Fri 14 Aug · 18:20", and who recorded it, when. */
        val occurred: String = "",
        val recorded: String = "",
        val isNew: Boolean = false,
    ) : GroupFeedRow
}

/** [date] keys the day in a list: [label] has no year, so it repeats a year apart. */
data class GroupDay(val date: LocalDate, val label: String, val rows: List<GroupFeedRow>)

/** #64: the group screen opens on Expenses. */
enum class GroupTab { Expenses, Balances, Activity }

/** #63: "with Aye · ฿350", and the payment Record payment opens with. */
data class SettleUpUiModel(val fromId: String, val toId: String, val line: String)

sealed interface GroupDetailUiState {
    data object Missing : GroupDetailUiState

    data class Loaded(
        val groupId: String,
        val name: String,
        val members: List<MemberChipUiModel>,
        val balanceLabel: String,
        val balance: Satang,
        val tone: BalanceTone,
        val spendLine: String,
        /** #48: what [balance] and every row count in. */
        val money: HomeMoney,
        val settled: Boolean,
        /** #63: the one main action, the largest debt that involves you; null when you have nothing to settle. */
        val settleUp: SettleUpUiModel?,
        /** #63: "Nothing to settle yet" or "All settled up" in place of Settle up, or null when [settleUp] shows. */
        val balanceChip: String?,
        /** #63: a new group shows no ฿0.00, only the chip. */
        val hasActivity: Boolean,
        /** #62: "3 people", or "Just you so far". */
        val memberLine: String,
        val days: List<GroupDay>,
        /** #189: "Up to date", "Offline · 3 changes waiting", or null for a group not shared yet. */
        val syncLine: String? = null,
        /** #190: whether this build can share at all, and whether this phone may change the link. */
        val canInvite: Boolean = false,
        val isCreator: Boolean = false,
        val invite: InviteUiModel? = null,
        /** #201: "Mali hasn't joined yet", said in words beside the faded chips. */
        val notJoinedLine: String? = null,
        /** #182: the repeats still running, each opening #185 to change or stop it. */
        val repeats: List<RepeatRowUiModel> = emptyList(),
        /** Edit group starts from these. */
        val type: GroupType = GroupType.Other,
        val icon: String = "",
        /** #199: this phone's own PromptPay ID in the group, masked, or null when none is set. */
        val myPromptPay: String? = null,
        /** What the menu's last item does to this group, see [GroupRemoval]. */
        val removal: GroupRemoval = GroupRemoval.DeleteHere,
        /** #218: a shared group on a phone whose session ended. The hero offers Sign in. */
        val signedOut: Boolean = false,
        /** #218: everyone but you, for Remove someone; and the people Add people can pick from. */
        val others: List<GroupPersonUiModel> = emptyList(),
        val canRemovePeople: Boolean = false,
        val addable: List<Person> = emptyList(),
        /** #218: a removed payment leaves a line in Activity only where the group syncs. */
        val isShared: Boolean = false,
        /** Pull-to-refresh: only a shared group in a build that syncs has anything to pull. */
        val canRefresh: Boolean = false,
        /** #74: Try again beside [syncLine], while changes wait or the last sync failed. */
        val canRetrySync: Boolean = false,
        /** #75: the header's avatars, you first, at most three; [moreMembers] counts the rest. */
        val headerMembers: List<MemberChipUiModel> = members.take(HEADER_AVATARS),
        val moreMembers: MoreMembersUiModel? = null,
    ) : GroupDetailUiState
}

/** The "···" menu's rows, in order; [GroupMenuEntry.GroupSettings] opens [settingsEntries]. */
enum class GroupMenuEntry { WhoPaysWhom, RepeatAnExpense, Activity, GroupSettings }

/** The Group settings step: what can change about the group, and the way out last. */
enum class GroupSettingsEntry { EditGroup, MyPromptPay, AddPeople, RemoveSomeone, LeaveOrDelete }

fun GroupDetailUiState.Loaded.menuEntries(): List<GroupMenuEntry> = GroupMenuEntry.entries

fun GroupDetailUiState.Loaded.settingsEntries(): List<GroupSettingsEntry> = listOfNotNull(
    GroupSettingsEntry.EditGroup,
    GroupSettingsEntry.MyPromptPay,
    GroupSettingsEntry.AddPeople,
    GroupSettingsEntry.RemoveSomeone.takeIf { canRemovePeople && others.isNotEmpty() },
    GroupSettingsEntry.LeaveOrDelete,
)

/** #75: "+9" beside the header's avatars, read out as "9 more people". */
data class MoreMembersUiModel(val label: String, val description: String)

internal const val HEADER_AVATARS = 3

/** One name in the group besides yours. [settled] is false while they owe or are owed here. */
data class GroupPersonUiModel(val id: String, val name: String, val settled: Boolean)

/**
 * Edit group and Delete group: working while the server answers, then an error or done. [waiting]
 * counts changes not sent yet when Leave stopped to ask first (#218).
 */
data class GroupActionState(val working: Boolean = false, val error: String? = null, val waiting: Int = 0)

/** "Rent" and "The 1st of each month · ฿18,000". */
data class RepeatRowUiModel(val id: String, val name: String, val line: String)

/** #190's invite sheet. [link] is what goes to LINE; [code] is the same, typed by hand. */
data class InviteUiModel(val code: String, val link: String, val active: Boolean)

data class InviteActionState(val working: Boolean = false, val error: String? = null)
