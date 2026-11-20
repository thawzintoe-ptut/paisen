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
import com.ptut.expense.domain.shared.GroupDetail
import com.ptut.expense.domain.shared.GroupFeedItem
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.domain.shared.PromptPay
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.formatMinor
import com.ptut.expense.domain.shared.moneyFormat
import com.ptut.expense.domain.timeOfDayIn
import com.ptut.expense.locale.AppText
import com.ptut.expense.sync.GroupSyncStatus
import com.ptut.expense.sync.InviteLinks
import com.ptut.expense.sync.SyncHealth

/**
 * #137's group screen from [GroupDetail]: the members, your balance and the spend in the group's
 * money (#48), and the feed by day (p27). Words only; every figure comes worked out.
 */
internal class GroupDetailMapper(private val categories: List<Category>) {

    fun loaded(detail: GroupDetail, status: GroupSyncStatus, syncAvailable: Boolean): GroupDetailUiState.Loaded =
        detail.loaded().withSync(detail, status, syncAvailable)

    private fun GroupDetailUiState.Loaded.withSync(detail: GroupDetail, status: GroupSyncStatus, syncAvailable: Boolean): GroupDetailUiState.Loaded {
        val chips = members.map { chip -> chip.copy(joined = chip.id in detail.joined) }
        // withStatus reads each chip's joined flag, so the chips go in first.
        return withChips(chips).withStatus(detail, status, syncAvailable)
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

    private fun GroupDetailUiState.Loaded.withStatus(detail: GroupDetail, status: GroupSyncStatus, syncAvailable: Boolean): GroupDetailUiState.Loaded {
        val group = detail.group
        val syncs = group.remote && syncAvailable
        return copy(
            notJoinedLine = members.filterNot { it.joined }.map { it.name }.joinedLine(),
            syncLine = status.syncLine(),
            canInvite = syncAvailable,
            canRefresh = syncs,
            canRetrySync = syncs && status.offersRetry(),
            isCreator = detail.isCreator,
            removal = detail.removal,
            invite = group.inviteCode?.let { InviteUiModel(it, InviteLinks.link(it), group.inviteActive) },
            signedOut = status.health == SyncHealth.SignedOut,
            canRemovePeople = detail.canRemovePeople,
        )
    }

    private fun GroupDetail.loaded(): GroupDetailUiState.Loaded {
        val text = AppText.copy.shared
        val money = group.moneyFormat()
        val feed = days.map { day -> GroupDay(day.date, dayHeading(day.date, today), day.items.map { it.row(group) }) }
        val settleUp = settleUpRow()
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
            spendLine = if (feed.isEmpty()) {
                text.detail.nothingSpent(group.currency)
            } else {
                text.detail.spendLine(group.type == GroupType.Trip, totalSpend.formatCompact(money), group.currency)
            },
            money = money,
            settled = everyoneSquare,
            settleUp = settleUp,
            balanceChip = when {
                feed.isEmpty() -> text.detail.nothingToSettle
                settleUp != null -> null
                everyoneSquare -> text.detail.allSettledUp
                else -> text.detail.settledHere
            },
            hasActivity = feed.isNotEmpty(),
            memberLine = if (group.activeMembers.size <= 1) text.detail.justYou else text.home.peopleCount(group.activeMembers.size),
            days = feed,
            type = group.type,
            icon = group.icon,
            myPromptPay = group.member(group.meMemberId.orEmpty())?.promptPayId?.let(PromptPay::masked),
            repeats = repeats.map { (rule, amount) ->
                RepeatRowUiModel(id = rule.id, name = rule.description, line = "${rule.anchorLine()}${Separators.DOT}${amount.formatCompact(money)}")
            },
            others = group.activeMembers.filterNot { group.isMe(it.id) }.map { member ->
                GroupPersonUiModel(member.id, member.name, settled = (balances[member.id] ?: Satang.Zero) == Satang.Zero)
            },
            addable = addable,
        )
    }

    /** You first, as "You"; everyone else by their initial. */
    private fun SharedGroup.chips(): List<MemberChipUiModel> {
        val you = AppText.copy.common.you
        return activeMembers
            .sortedBy { !isMe(it.id) }
            .map { MemberChipUiModel(it.id, if (isMe(it.id)) you else it.initial(), isMe(it.id), name = if (isMe(it.id)) you else it.name) }
    }

    /** #63: settle with the other side of the largest debt that involves you. */
    private fun GroupDetail.settleUpRow(): SettleUpUiModel? = settleUp?.let { transfer ->
        val other = if (transfer.fromMemberId == group.meMemberId) transfer.toMemberId else transfer.fromMemberId
        val line = AppText.copy.shared.detail.settleWith(group.displayName(other), transfer.amount.formatCompact(group.moneyFormat()))
        SettleUpUiModel(transfer.fromMemberId, transfer.toMemberId, line)
    }

    private fun GroupFeedItem.row(group: SharedGroup): GroupFeedRow = when (this) {
        is GroupFeedItem.Expense -> expenseRow(group)
        is GroupFeedItem.Payment -> paymentRow(group)
    }

    private fun GroupFeedItem.Payment.paymentRow(group: SharedGroup): GroupFeedRow.Payment {
        val text = AppText.copy.shared
        val author = payment.authorMemberId?.let { group.displayName(it) } ?: text.someone
        return GroupFeedRow.Payment(
            id = payment.id,
            title = text.detail.paid(group.displayName(payment.fromMemberId), group.displayName(payment.toMemberId)),
            detail = text.detail.settlement(payment.method.label()),
            amount = payment.amount.format(group.moneyFormat()),
            occurred = payment.occurredAtEpochMillis.dayAndTime(),
            recorded = text.log.recordedBy(author, payment.createdAtEpochMillis.dayAndTime()),
            isNew = isNew,
        )
    }

    /** "Tom paid ₫1,850,000", then what it means for you in the group's money: you owe, you lent, or not involved. */
    private fun GroupFeedItem.Expense.expenseRow(group: SharedGroup): GroupFeedRow.Expense {
        val text = AppText.copy.shared
        val (involved, effect, mine) = part
        val category = categories.firstOrNull { it.id == expense.categoryId }
        return GroupFeedRow.Expense(
            id = expense.id,
            icon = category?.icon ?: DEFAULT_ICON,
            title = ExpenseTitle.shown(expense.note, expense.description, category?.name),
            detail = text.detail.payerPaid(group.displayName(expense.payerMemberId), formatMinor(expense.amountMinor, Currencies.of(expense.currency))),
            word = when {
                !involved -> text.detail.notInvolved
                effect > Satang.Zero -> text.detail.youLent
                effect < Satang.Zero -> text.youOwe
                else -> text.detail.yourShare
            },
            amount = (if (effect == Satang.Zero) mine else effect.magnitude()).format(group.moneyFormat()),
            tone = if (involved) effect.tone() else BalanceTone.Settled,
            isNew = isNew,
        )
    }

    private companion object {
        const val DEFAULT_ICON = "receipt_long"
    }
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
