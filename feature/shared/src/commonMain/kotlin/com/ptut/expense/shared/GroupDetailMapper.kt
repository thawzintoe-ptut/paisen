package com.ptut.expense.shared

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.HomeTime
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Separators
import com.ptut.expense.domain.dateIn
import com.ptut.expense.domain.dayLabel
import com.ptut.expense.domain.format
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.shared.Currencies
import com.ptut.expense.domain.shared.ExpenseTitle
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.PromptPay
import com.ptut.expense.domain.shared.SharedExpense
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.SharedSnapshot
import com.ptut.expense.domain.shared.convert
import com.ptut.expense.domain.shared.currentExpenses
import com.ptut.expense.domain.shared.effectFor
import com.ptut.expense.domain.shared.formatMinor
import com.ptut.expense.domain.shared.money
import com.ptut.expense.domain.shared.moneyFormat
import com.ptut.expense.domain.shared.myNet
import com.ptut.expense.domain.shared.nets
import com.ptut.expense.domain.shared.paymentsIn
import com.ptut.expense.domain.shared.running
import com.ptut.expense.domain.shared.totalSpend
import com.ptut.expense.domain.shared.whoPaysWhom
import com.ptut.expense.domain.sum
import com.ptut.expense.domain.timeOfDayIn
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.GroupSyncStatus
import com.ptut.expense.sync.InviteLinks
import com.ptut.expense.sync.SyncHealth
import kotlinx.datetime.LocalDate

/**
 * #137's group screen from one snapshot: the members, your balance and the spend in the group's
 * money (#48), and the feed by day (p27). [today] and [seen] come in, so nothing here reads a clock.
 */
internal class GroupDetailMapper(private val categories: List<Category>) {

    fun loaded(
        snapshot: SharedSnapshot,
        group: SharedGroup,
        seen: Long?,
        today: LocalDate,
        status: GroupSyncStatus,
        syncAvailable: Boolean,
    ): GroupDetailUiState.Loaded = snapshot.loaded(group, seen, today).withSync(group, status, syncAvailable)

    private fun GroupDetailUiState.Loaded.withSync(group: SharedGroup, status: GroupSyncStatus, syncAvailable: Boolean): GroupDetailUiState.Loaded {
        val chips = members.map { chip -> chip.copy(joined = !group.remote || chip.isMe || group.member(chip.id)?.claimedByUid != null) }
        // withStatus reads each chip's joined flag, so the chips go in first.
        return withChips(chips).withStatus(group, status, syncAvailable)
    }

    private fun GroupDetailUiState.Loaded.withChips(chips: List<MemberChipUiModel>): GroupDetailUiState.Loaded {
        val more = chips.size - HEADER_AVATARS
        val text = AppText.copy.shared.detail
        return copy(
            members = chips,
            headerMembers = chips.take(HEADER_AVATARS),
            moreMembers = if (more > 0) MoreMembersUiModel(text.moreAvatars(more), text.morePeople(more)) else null,
        )
    }

    private fun GroupDetailUiState.Loaded.withStatus(group: SharedGroup, status: GroupSyncStatus, syncAvailable: Boolean): GroupDetailUiState.Loaded {
        val syncs = group.remote && syncAvailable
        val createdHere = group.createdHere()
        return copy(
            notJoinedLine = members.filterNot { it.joined }.map { it.name }.joinedLine(),
            syncLine = status.syncLine(),
            canInvite = syncAvailable,
            canRefresh = syncs,
            canRetrySync = syncs && status.offersRetry(),
            isCreator = !group.remote || group.creatorUid == null || createdHere,
            removal = group.removal(),
            invite = group.inviteCode?.let { InviteUiModel(it, InviteLinks.link(it), group.inviteActive) },
            signedOut = status.health == SyncHealth.SignedOut,
            canRemovePeople = !group.remote || createdHere,
        )
    }

    private fun SharedSnapshot.loaded(group: SharedGroup, seen: Long?, today: LocalDate): GroupDetailUiState.Loaded {
        val text = AppText.copy.shared
        val net = myNet(group.id)
        val money = group.moneyFormat()
        val balances = nets(group.id)
        val allSquare = balances.values.all { it == Satang.Zero }
        val days = feed(group, seen, today)
        val settleUp = settleUp(group)
        return GroupDetailUiState.Loaded(
            groupId = group.id,
            name = group.name,
            isShared = group.remote,
            members = group.chips(),
            balanceLabel = when (net.tone()) {
                BalanceTone.Owed -> text.detail.owedHere
                BalanceTone.Owe -> text.detail.oweHere
                BalanceTone.Settled -> text.detail.settledHere
            },
            balance = net.magnitude(),
            tone = net.tone(),
            spendLine = if (days.isEmpty()) {
                text.detail.nothingSpent(group.currency)
            } else {
                text.detail.spendLine(group.type == GroupType.Trip, totalSpend(group.id).formatCompact(money), group.currency)
            },
            money = money,
            settled = allSquare,
            settleUp = settleUp,
            balanceChip = when {
                days.isEmpty() -> text.detail.nothingToSettle
                settleUp != null -> null
                allSquare -> text.detail.allSettledUp
                else -> text.detail.settledHere
            },
            hasActivity = days.isNotEmpty(),
            memberLine = if (group.activeMembers.size <= 1) text.detail.justYou else text.home.peopleCount(group.activeMembers.size),
            days = days,
            type = group.type,
            icon = group.icon,
            myPromptPay = group.member(group.meMemberId.orEmpty())?.promptPayId?.let(PromptPay::masked),
            repeats = repeatRows(group, today),
            others = group.activeMembers.filterNot { group.isMe(it.id) }.map { member ->
                GroupPersonUiModel(member.id, member.name, settled = (balances[member.id] ?: Satang.Zero) == Satang.Zero)
            },
            addable = people.filter { person -> !person.archived && group.activeMembers.none { it.personId == person.id } },
        )
    }

    /** You first, as "You"; everyone else by their initial. */
    private fun SharedGroup.chips(): List<MemberChipUiModel> {
        val you = AppText.copy.common.you
        return activeMembers
            .sortedBy { !isMe(it.id) }
            .map { MemberChipUiModel(it.id, if (isMe(it.id)) you else it.initial(), isMe(it.id), name = if (isMe(it.id)) you else it.name) }
    }

    /** Expenses and payments together, newest first, by day. */
    private fun SharedSnapshot.feed(group: SharedGroup, seen: Long?, today: LocalDate): List<GroupDay> {
        val text = AppText.copy.shared
        val money = group.moneyFormat()
        val expenseRows = currentExpenses(group.id).map { it.occurredAtEpochMillis to it.row(group, seen) }
        val paymentRows = paymentsIn(group.id).map { payment ->
            val author = payment.authorMemberId?.let { group.displayName(it) } ?: text.someone
            payment.occurredAtEpochMillis to GroupFeedRow.Payment(
                id = payment.id,
                title = text.detail.paid(group.displayName(payment.fromMemberId), group.displayName(payment.toMemberId)),
                detail = text.detail.settlement(payment.method.label()),
                amount = payment.amount.format(money),
                occurred = payment.occurredAtEpochMillis.dayAndTime(),
                recorded = text.log.recordedBy(author, payment.createdAtEpochMillis.dayAndTime()),
                isNew = group.isNewToMe(payment.authorMemberId, payment.createdAtEpochMillis, seen),
            )
        }
        return (expenseRows + paymentRows)
            .sortedByDescending { it.first }
            .groupBy { (at, _) -> at.dateIn(HomeTime) }
            .map { (date, rows) -> GroupDay(date, dayHeading(date, today), rows.map { it.second }) }
    }

    /** #63: the largest debt that involves you, or null when you have nothing to settle. */
    private fun SharedSnapshot.settleUp(group: SharedGroup): SettleUpUiModel? {
        val me = group.meMemberId
        return whoPaysWhom(group.id).transfers
            .filter { it.fromMemberId == me || it.toMemberId == me }
            .maxByOrNull { it.amount }
            ?.let { transfer ->
                val other = if (transfer.fromMemberId == me) transfer.toMemberId else transfer.fromMemberId
                val line = AppText.copy.shared.detail.settleWith(group.displayName(other), transfer.amount.formatCompact(group.moneyFormat()))
                SettleUpUiModel(transfer.fromMemberId, transfer.toMemberId, line)
            }
    }

    private fun SharedSnapshot.repeatRows(group: SharedGroup, today: LocalDate): List<RepeatRowUiModel> =
        repeats.filter { it.groupId == group.id && it.running(today) }.map { rule ->
            val amount = convert(rule.amountMinor, Currencies.of(rule.currency), rule.rate, group.money).formatCompact(group.moneyFormat())
            RepeatRowUiModel(id = rule.id, name = rule.description, line = "${rule.anchorLine()}${Separators.DOT}$amount")
        }

    /** "Tom paid ₫1,850,000", then what it means for you in the group's money: you owe, you lent, or not involved. */
    private fun SharedExpense.row(group: SharedGroup, seen: Long?): GroupFeedRow.Expense {
        val text = AppText.copy.shared
        val me = group.meMemberId
        val mine = shares.filter { it.memberId == me }.map { it.satang }.sum()
        val effect = effectFor(me)
        val involved = payerMemberId == me || shares.any { it.memberId == me }
        return GroupFeedRow.Expense(
            id = id,
            icon = categories.firstOrNull { it.id == categoryId }?.icon ?: DEFAULT_ICON,
            title = ExpenseTitle.shown(note, description, categories.firstOrNull { it.id == categoryId }?.name),
            detail = text.detail.payerPaid(group.displayName(payerMemberId), formatMinor(amountMinor, Currencies.of(currency))),
            word = when {
                !involved -> text.detail.notInvolved
                effect > Satang.Zero -> text.detail.youLent
                effect < Satang.Zero -> text.youOwe
                else -> text.detail.yourShare
            },
            amount = (if (effect == Satang.Zero) mine else effect.magnitude()).format(group.moneyFormat()),
            tone = if (involved) effect.tone() else BalanceTone.Settled,
            isNew = group.isNewToMe(authorMemberId, createdAtEpochMillis, seen),
        )
    }

    private companion object {
        const val DEFAULT_ICON = "receipt_long"
    }
}

/** The same rule the repository and Firestore's rules hold: only the creator removes a name. */
internal fun SharedGroup.createdHere(): Boolean {
    val myUid = member(meMemberId.orEmpty())?.claimedByUid
    return creatorUid != null && creatorUid == myUid
}

/** What the menu's last item does to this group, see [GroupRemoval]. */
internal fun SharedGroup.removal(): GroupRemoval = when {
    !remote -> GroupRemoval.DeleteHere
    createdHere() -> GroupRemoval.DeleteForEveryone
    else -> GroupRemoval.Leave
}

/** #74: a failed sync, or changes still waiting, offer Try again; signed out asks to sign in instead. */
internal fun GroupSyncStatus.offersRetry(): Boolean = health == SyncHealth.Offline || health == SyncHealth.Waiting

/** #189: "Up to date", "Offline · 3 changes waiting", or null for a group not shared yet. */
private fun GroupSyncStatus.syncLine(): String? {
    val text = AppText.copy.shared
    return when {
        // #194: said once, then cleared when the screen goes.
        refused > 0 -> text.detail.refused(refused)

        health == SyncHealth.Local -> null

        health == SyncHealth.SignedOut -> text.sync.signedOutGroup(waiting)

        health == SyncHealth.UpToDate -> text.detail.upToDate

        health == SyncHealth.Waiting -> text.changesWaiting(waiting)

        else -> text.offlineWaiting(waiting)
    }
}

fun PaymentMethod.label(): String {
    val text = AppText.copy.shared
    return when (this) {
        PaymentMethod.BankTransfer -> text.methodBankTransfer
        PaymentMethod.Cash -> text.methodCash
        PaymentMethod.PromptPay -> "PromptPay"
        PaymentMethod.OtherApp -> text.methodOtherApp
    }
}

/** "Mali hasn't joined yet", "Mali and Nok haven't joined yet", or "3 people haven't joined yet". */
private fun List<String>.joinedLine(): String? {
    val text = AppText.copy.shared.detail
    return when (size) {
        0 -> null
        1 -> text.hasNotJoined(single())
        2 -> text.twoHaveNotJoined(first(), last())
        else -> text.manyHaveNotJoined(size)
    }
}

/** "Fri 14 Aug · 18:20", in Bangkok time. */
private fun Long.dayAndTime(): String = "${dateIn(HomeTime).dayLabel()}${Separators.DOT}${timeOfDayIn(HomeTime)}"
