package com.ptut.expense.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkyKeyValueRow
import com.ptut.expense.ui.sky.SkySectionHeader
import com.ptut.expense.ui.sky.SkyTextAction
import com.ptut.expense.ui.sky.skyItem

/** #64: what the shell hands the group screen's tabs, read from Who pays whom's and Activity's own view models. */
data class GroupTabsState(
    val selected: GroupTab = GroupTab.Expenses,
    val onSelect: (GroupTab) -> Unit = {},
    val balances: WhoPaysWhomUiState = WhoPaysWhomUiState.Missing,
    val onSimplify: (Boolean) -> Unit = {},
    val activity: SharedActivityUiState? = null,
)

/** #62 and #63: icon, name, people, the balance in words, the sync line and the one main action. */
@Composable
internal fun GroupHeader(
    state: GroupDetailUiState.Loaded,
    onSignIn: () -> Unit,
    onSettleUp: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    refreshing: Boolean = false,
    onRetrySync: () -> Unit = {},
) {
    val text = LocalCopy.current.shared
    val on = MaterialTheme.ptut.heroOn
    Column(modifier.padding(horizontal = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            GroupTile(icon = state.icon, ink = true, size = SkySizes.groupFab)
            Column(Modifier.weight(1f)) {
                // A long or Burmese name wraps once, then ends with an ellipsis.
                Text(state.name, style = MaterialTheme.typography.headlineMedium, color = on, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(state.memberLine, style = MaterialTheme.typography.bodyLarge, color = on)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                state.headerMembers.forEach { member ->
                    MemberAvatar(
                        label = member.label,
                        isMe = member.isMe,
                        size = SkySizes.chip,
                        modifier = Modifier
                            .semantics { contentDescription = if (member.joined) member.name else text.detail.notJoined(member.name) }
                            .then(if (member.joined) Modifier else Modifier.alpha(NOT_JOINED_ALPHA)),
                    )
                }
                state.moreMembers?.let { more ->
                    MemberAvatar(
                        label = more.label,
                        isMe = false,
                        size = SkySizes.chip,
                        modifier = Modifier.semantics { contentDescription = more.description },
                    )
                }
            }
        }
        // #201: the fade is said in words too, so it never depends on contrast alone.
        state.notJoinedLine?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = on, modifier = Modifier.padding(top = Spacing.xxs))
        }
        if (state.balanceChip == null) {
            SharedHero(
                label = state.balanceLabel,
                amount = state.balance,
                tint = state.tone.ink(),
                labelColor = on,
                money = state.money,
                modifier = Modifier.padding(top = Spacing.md),
            )
        } else {
            BalanceChip(state.balanceChip, Modifier.padding(top = Spacing.md))
        }
        Text(state.spendLine, style = MaterialTheme.typography.bodyMedium, color = on, modifier = Modifier.padding(top = Spacing.xs))
        state.syncLine?.let { line ->
            Row(Modifier.padding(top = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                Text(line, style = MaterialTheme.typography.labelMedium, color = on, modifier = Modifier.weight(1f, fill = false))
                if (state.canRetrySync) {
                    SkyTextAction(label = LocalCopy.current.common.tryAgain, onClick = onRetrySync, enabled = !refreshing, ink = on)
                }
            }
        }
        if (state.signedOut) {
            SkyTextAction(label = text.signIn, onClick = onSignIn, enabled = true, ink = on, modifier = Modifier.offset(x = -Spacing.xs))
        }
        state.settleUp?.let { settle ->
            Row(Modifier.padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SkyButton(text.detail.settleUp, onClick = { onSettleUp(settle.fromId, settle.toId) })
                Text(settle.line, style = MaterialTheme.typography.bodyLarge, color = on, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** "✓ All settled up": a statement, never a control. */
@Composable
private fun BalanceChip(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = SkySizes.chip)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(PtutIcon.Check.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.chipGlyph))
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Expenses, Balances and Activity on a hairline, the chosen one underlined in ruby; Activity carries its count. */
@Composable
internal fun GroupTabRow(selected: GroupTab, activityCount: Int, onSelect: (GroupTab) -> Unit, modifier: Modifier = Modifier) {
    val copy = LocalCopy.current.shared
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup()) {
            GroupTab.entries.forEach { tab ->
                val chosen = tab == selected
                val label = when (tab) {
                    GroupTab.Expenses -> copy.detail.expenses
                    GroupTab.Balances -> copy.detail.balances
                    GroupTab.Activity -> copy.activity
                }
                val count = if (tab == GroupTab.Activity) activityCount else 0
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = SkySizes.tab)
                        .selectable(selected = chosen, role = Role.Tab, onClick = { onSelect(tab) })
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (count > 0) "$label, ${copy.detail.newActivity(count)}" else label
                        },
                ) {
                    Column(Modifier.width(IntrinsicSize.Max).fillMaxHeight()) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.s6),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                            if (count > 0) CountBadge(count)
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(SkySizes.tabUnderline)
                                .background(if (chosen) MaterialTheme.ptut.signature else Color.Transparent),
                        )
                    }
                }
            }
        }
        SkyHairline()
    }
}

@Composable
private fun CountBadge(count: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .sizeIn(minWidth = SkySizes.statusPill, minHeight = SkySizes.statusPill)
            .clip(CircleShape)
            .background(MaterialTheme.ptut.signature)
            .padding(horizontal = Spacing.xxs)
            .clearAndSetSemantics {},
    ) {
        Text(LocalCopy.current.shared.detail.badgeCount(count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.ptut.onSignature)
    }
}

/** #65: a group with nothing in it says what an expense does here, then the two ways forward. */
internal fun LazyListScope.expensesTab(
    state: GroupDetailUiState.Loaded,
    onOpenRepeat: (String) -> Unit,
    onOpenExpense: (String) -> Unit,
    onOpenPayment: (String) -> Unit,
    onAddExpense: () -> Unit,
    onInvite: () -> Unit,
) {
    if (state.repeats.isNotEmpty()) {
        item(key = "repeats") {
            Column(skyItem().padding(horizontal = Spacing.md)) {
                SkySectionHeader(text = LocalCopy.current.shared.detail.repeats)
                state.repeats.forEach { repeat ->
                    SkyKeyValueRow(
                        label = repeat.name,
                        value = repeat.line,
                        modifier = Modifier.tappable(PtutRadii.r12, Role.Button) { onOpenRepeat(repeat.id) },
                    )
                }
            }
        }
    }
    if (state.days.isEmpty()) {
        item(key = "empty") { NoExpensesYet(canInvite = state.canInvite, onAddExpense = onAddExpense, onInvite = onInvite, modifier = skyItem()) }
    }
    state.days.forEach { day ->
        item(key = "day-${day.date}") {
            Text(
                day.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = skyItem().padding(horizontal = Spacing.s20).padding(top = Spacing.sm, bottom = Spacing.xxs),
            )
        }
        items(day.rows, key = { it.id }) { row ->
            Box(skyItem()) {
                when (row) {
                    is GroupFeedRow.Expense -> ExpenseRow(row, onClick = { onOpenExpense(row.id) })
                    is GroupFeedRow.Payment -> PaymentRow(row, onClick = { onOpenPayment(row.id) })
                }
            }
        }
    }
}

@Composable
private fun NoExpensesYet(canInvite: Boolean, onAddExpense: () -> Unit, onInvite: () -> Unit, modifier: Modifier = Modifier) {
    val text = LocalCopy.current.shared
    Column(modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md)) {
        Text(text.detail.noExpensesTitle, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(text.detail.noExpensesBody, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        SkyButton(text.detail.addAnExpense, onClick = onAddExpense, modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg))
        if (canInvite) {
            SkyButton(text.invitePeople, onClick = onInvite, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12))
        }
    }
}

/** #64: Who pays whom in place; with nobody to settle with, "Everyone's square" and who is in the group. */
internal fun LazyListScope.balancesTab(
    state: GroupDetailUiState.Loaded,
    balances: WhoPaysWhomUiState,
    onSimplify: (Boolean) -> Unit,
    onSettle: (String, String) -> Unit,
) {
    val loaded = balances as? WhoPaysWhomUiState.Loaded ?: return
    if (loaded.cards.isNotEmpty()) {
        item(key = "balances") { WhoPaysWhomContent(loaded, onSimplify = onSimplify, onSettle = onSettle, modifier = skyItem().padding(top = Spacing.sm)) }
        return
    }
    item(key = "square") {
        val text = LocalCopy.current.shared.detail
        Column(skyItem().padding(horizontal = Spacing.md).padding(top = Spacing.md)) {
            Text(text.everyoneSquare, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            SkySectionHeader(text = text.whoIsIn)
        }
    }
    items(state.members, key = { "member-${it.id}" }) { member ->
        Row(
            modifier = skyItem().fillMaxWidth().heightIn(min = SkySizes.transactionRow).padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            MemberAvatar(label = member.label, isMe = member.isMe)
            Text(member.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** #64: unseen changes first under their own heading, then the rest by day. */
internal fun LazyListScope.activityTab(activity: SharedActivityUiState?, onOpenExpense: (String) -> Unit, onOpenPayment: (String) -> Unit) {
    val state = activity ?: return
    fun open(entry: SharedEntryUiModel): (() -> Unit)? = entry.expenseId?.let { id -> { onOpenExpense(id) } }
        ?: entry.paymentId?.let { id -> { onOpenPayment(id) } }
    if (state.fresh.isEmpty() && state.days.isEmpty()) {
        item(key = "activity-empty") {
            Text(
                LocalCopy.current.shared.detail.activityEmpty,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = skyItem().padding(horizontal = Spacing.md).padding(top = Spacing.md),
            )
        }
        return
    }
    if (state.fresh.isNotEmpty()) {
        item(key = "activity-new") { ActivityHeading(LocalCopy.current.shared.detail.newSinceLooked.uppercase(), skyItem()) }
        items(state.fresh, key = { "new-${it.id}" }) { entry -> Box(skyItem()) { ActivityEntryRow(entry, isNew = true, onClick = open(entry)) } }
        // 33c: the rest sits under its own heading, so the line between new and seen is said.
        if (state.days.isNotEmpty()) item(key = "activity-earlier") { ActivityHeading(LocalCopy.current.shared.detail.earlier.uppercase(), skyItem()) }
    }
    activityDays(state.days, itemModifier = { skyItem() }, open = ::open)
}
