package com.ptut.expense.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ptut.expense.design.GlassBackdrop
import com.ptut.expense.design.GlassSurface
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.glassSource
import com.ptut.expense.design.ptut
import com.ptut.expense.design.rememberGlassBackdrop
import com.ptut.expense.design.tappable
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.Person
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.platform.notify.rememberNotificationAsk
import com.ptut.expense.sync.push.pushConfigured
import com.ptut.expense.ui.sky.SkyBarIconAction
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyChange
import com.ptut.expense.ui.sky.SkyChip
import com.ptut.expense.ui.sky.SkyEmptyState
import com.ptut.expense.ui.sky.SkyKeyValueRow
import com.ptut.expense.ui.sky.SkyPageSheet
import com.ptut.expense.ui.sky.SkyProgress
import com.ptut.expense.ui.sky.SkySectionHeader
import com.ptut.expense.ui.sky.SkyTextAction
import com.ptut.expense.ui.sky.SkyTopBar
import com.ptut.expense.ui.sky.skyItem

/** #137: p27's sky field over a white sheet of expenses and payments, with the ink add button. */
@Composable
fun GroupDetailView(
    uiState: GroupDetailUiState,
    onBack: () -> Unit,
    onSettleUp: (from: String, to: String) -> Unit,
    onBalances: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (String) -> Unit,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
    invite: GroupInvite = GroupInvite(),
    /** #185: set up a repeat, or open one to change or stop it. */
    onRepeat: () -> Unit = {},
    onOpenRepeat: (String) -> Unit = {},
    /** Edit group, and Delete or Leave from the "···" menu. */
    manage: GroupManage = GroupManage(),
) {
    val text = LocalCopy.current.shared
    when (uiState) {
        GroupDetailUiState.Missing -> Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) {
            SkyTopBar(title = text.group, onBack = onBack)
            SkyEmptyState(
                headline = text.thisGroupIsGone,
                body = text.detail.goneBody,
                status = text.detail.goneStatus,
                statusIcon = PtutIcon.Check.image,
                modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md),
            )
        }

        is GroupDetailUiState.Loaded -> Loaded(uiState, onBack, onSettleUp, onBalances, onAddExpense, onOpenExpense, onActivity, invite, onRepeat, onOpenRepeat, manage, modifier)
    }
}

@Composable
private fun Loaded(
    state: GroupDetailUiState.Loaded,
    onBack: () -> Unit,
    onSettleUp: (String, String) -> Unit,
    onBalances: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (String) -> Unit,
    onActivity: () -> Unit,
    invite: GroupInvite,
    onRepeat: () -> Unit,
    onOpenRepeat: (String) -> Unit,
    manage: GroupManage,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.shared
    var menu by rememberSaveable { mutableStateOf(false) }
    var inviteStep by rememberSaveable { mutableStateOf<String?>(null) }
    val ask = rememberNotificationAsk()
    // A shared group's changes arrive as notifications, so the app asks once it is shared, also for
    // members who joined before push existed. Not over the invite sheet; the OS stops asking once answered.
    LaunchedEffect(state.isShared, inviteStep == null) {
        if (state.isShared && inviteStep == null && pushConfigured) ask.request()
    }
    var manageStep by rememberSaveable { mutableStateOf<String?>(null) }
    var openPayment by rememberSaveable { mutableStateOf<String?>(null) }
    var askRemovePayment by rememberSaveable { mutableStateOf(false) }
    var autoOpened by rememberSaveable { mutableStateOf(false) }
    // Opened by itself after Create, the sheet asks first: nothing reaches Firebase until
    // "Make an invite link" is tapped. Invite people in the menu is that tap already.
    var askFirst by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(invite.openOnStart, state.canInvite) {
        if (invite.openOnStart && state.canInvite && !autoOpened) {
            autoOpened = true
            askFirst = true
            inviteStep = if (invite.signedIn) STEP_SHEET else STEP_SIGN_IN
        }
    }
    // The feed scrolls under the add button, which shows it through iOS glass (#123).
    val glass = rememberGlassBackdrop()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) {
        LazyColumn(Modifier.fillMaxSize().glassSource(glass)) {
            item {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.ptut.heroBg).padding(bottom = Spacing.lg)) {
                    SkyTopBar(
                        title = state.name,
                        onBack = onBack,
                        trailing = { SkyBarIconAction(icon = PtutIcon.MoreHoriz, label = text.detail.more, onClick = { menu = true }) },
                    )
                    Column(Modifier.padding(horizontal = Spacing.md)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                            state.members.forEach { member ->
                                // #190: a name nobody has claimed yet is drawn faint until they join.
                                MemberAvatar(
                                    label = member.label,
                                    isMe = member.isMe,
                                    size = SkySizes.chip,
                                    modifier = Modifier
                                        .semantics { contentDescription = if (member.joined) member.name else text.detail.notJoined(member.name) }
                                        .then(if (member.joined) Modifier else Modifier.alpha(NOT_JOINED_ALPHA)),
                                )
                            }
                        }
                        // #201: the fade is said in words too, so it never depends on contrast alone.
                        state.notJoinedLine?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.ptut.heroOn, modifier = Modifier.padding(top = Spacing.xxs))
                        }
                        SharedHero(
                            label = state.balanceLabel,
                            amount = state.balance,
                            tint = MaterialTheme.ptut.heroOn,
                            modifier = Modifier.padding(top = Spacing.md),
                        )
                        Text(
                            state.spendLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.ptut.heroOn,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                        SkyChange(target = state.syncLine) { line ->
                            line?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.ptut.heroOn, modifier = Modifier.padding(top = Spacing.xxs)) }
                        }
                        if (state.signedOut) {
                            SkyTextAction(
                                label = text.signIn,
                                onClick = { inviteStep = STEP_SIGN_IN_AGAIN },
                                enabled = true,
                                ink = MaterialTheme.ptut.heroOn,
                                modifier = Modifier.offset(x = -Spacing.xs),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
                        ) {
                            if (!state.settled) {
                                SkyButton(
                                    text.detail.settleUp,
                                    onClick = { state.settleWith?.let { (from, to) -> onSettleUp(from, to) } ?: onBalances() },
                                    modifier = Modifier.weight(1.3f),
                                )
                            }
                            SkyButton(text.detail.balances, onClick = onBalances, kind = SkyButtonKind.Outline, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                SkyPageSheet(modifier = Modifier.padding(top = Spacing.none)) {
                    // #182: what adds itself, above what was added.
                    if (state.repeats.isNotEmpty()) {
                        SkySectionHeader(text = text.detail.repeats)
                        state.repeats.forEach { repeat ->
                            SkyKeyValueRow(
                                label = repeat.name,
                                value = repeat.line,
                                modifier = Modifier.tappable(PtutRadii.r12, Role.Button) { onOpenRepeat(repeat.id) },
                            )
                        }
                    }
                    SkySectionHeader(text = text.detail.expenses)
                    if (state.days.isEmpty()) {
                        Text(
                            text.detail.noExpenses,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
            state.days.forEach { day ->
                item(key = "day-${day.label}") {
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
                            is GroupFeedRow.Payment -> PaymentRow(row, onClick = { openPayment = row.id })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(SkySizes.pillClearance)) }
        }
        AddButton(glass, onClick = onAddExpense, modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.md))
    }

    if (menu) {
        SkyBottomSheet(onDismissRequest = { menu = false }) {
            Text(state.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
            SkyButton(
                text.detail.editGroup,
                onClick = {
                    menu = false
                    manageStep = STEP_EDIT
                },
                kind = SkyButtonKind.Outline,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            )
            SkyButton(
                text.detail.myPromptPay,
                onClick = {
                    menu = false
                    manageStep = STEP_PROMPTPAY
                },
                kind = SkyButtonKind.Outline,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12),
            )
            SkyButton(
                text.detail.addPeople,
                onClick = {
                    menu = false
                    manageStep = STEP_ADD_PEOPLE
                },
                kind = SkyButtonKind.Outline,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12),
            )
            if (state.canRemovePeople && state.others.isNotEmpty()) {
                SkyButton(
                    text.detail.removeSomeone,
                    onClick = {
                        menu = false
                        manageStep = STEP_REMOVE_PEOPLE
                    },
                    kind = SkyButtonKind.Outline,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12),
                )
            }
            SkyButton(
                text.activity,
                onClick = {
                    menu = false
                    onActivity()
                },
                kind = SkyButtonKind.Outline,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12),
            )
            SkyButton(
                text.whoPaysWhom,
                onClick = {
                    menu = false
                    onBalances()
                },
                kind = SkyButtonKind.Outline,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12),
            )
            SkyButton(
                text.detail.repeatAnExpense,
                onClick = {
                    menu = false
                    onRepeat()
                },
                kind = SkyButtonKind.Outline,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12),
            )
            if (state.canInvite) {
                SkyButton(
                    text.invitePeople,
                    onClick = {
                        menu = false
                        askFirst = false
                        if (invite.signedIn) {
                            invite.onInvite()
                            inviteStep = STEP_SHEET
                        } else {
                            inviteStep = STEP_SIGN_IN
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12),
                )
            }
            SkyButton(
                state.removal.label(),
                onClick = {
                    menu = false
                    manageStep = STEP_REMOVE
                },
                kind = SkyButtonKind.DangerOutline,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12),
            )
        }
    }

    when (manageStep) {
        STEP_EDIT -> EditGroupSheet(
            name = state.name,
            type = state.type,
            icon = state.icon,
            onSave = { name, type, icon ->
                manageStep = null
                manage.onEdit(name, type, icon)
            },
            onDismiss = { manageStep = null },
        )

        STEP_PROMPTPAY -> PromptPaySheet(
            current = state.myPromptPay,
            onSave = { typed -> manage.onSetPromptPay(typed).also { saved -> if (saved) manageStep = null } },
            onDismiss = { manageStep = null },
        )

        STEP_REMOVE -> RemoveGroupSheet(
            name = state.name,
            removal = state.removal,
            settled = state.settled,
            action = manage.action,
            onConfirm = manage.onRemove,
            onDismiss = {
                manageStep = null
                manage.onDismissError()
            },
        )

        STEP_ADD_PEOPLE -> AddPeopleSheet(
            people = state.addable,
            onAdd = { members ->
                manageStep = null
                manage.onAddPeople(members)
            },
            onDismiss = { manageStep = null },
        )

        STEP_REMOVE_PEOPLE -> RemovePeopleSheet(
            others = state.others,
            onRemove = manage.onRemoveMember,
            onDismiss = { manageStep = null },
        )
    }

    // Gone when another phone removed it meanwhile, and the sheet goes with it.
    val payment = state.days.flatMap { it.rows }.filterIsInstance<GroupFeedRow.Payment>().firstOrNull { it.id == openPayment }
    when {
        payment == null -> Unit

        askRemovePayment -> RemovePaymentSheet(
            shared = state.isShared,
            onConfirm = {
                askRemovePayment = false
                openPayment = null
                manage.onRemovePayment(payment.id)
            },
            onDismiss = { askRemovePayment = false },
        )

        else -> PaymentSheet(row = payment, onRemove = { askRemovePayment = true }, onDismiss = { openPayment = null })
    }

    when (inviteStep) {
        STEP_SIGN_IN -> SignInSheet(
            reason = text.detail.signInReason(state.name),
            onSignedIn = {
                if (!askFirst) invite.onInvite()
                inviteStep = STEP_SHEET
            },
            onDismiss = { inviteStep = null },
        )

        STEP_SHEET -> InviteSheet(
            groupName = state.name,
            invite = state.invite,
            action = invite.action,
            isCreator = state.isCreator,
            onNewLink = invite.onNewLink,
            onSwitchOff = invite.onSwitchOff,
            onDismiss = { inviteStep = null },
            onTryAgain = invite.onInvite,
            onMakeLink = if (askFirst && state.invite == null) {
                {
                    askFirst = false
                    invite.onInvite()
                }
            } else {
                null
            },
        )

        // The screen's polling syncs as soon as the account is back (#218).
        STEP_SIGN_IN_AGAIN -> SignInSheet(
            reason = text.sync.signInAgainReason,
            onSignedIn = { inviteStep = null },
            onDismiss = { inviteStep = null },
        )
    }
}

/** Edit group, people, and Delete or Leave: what the menu's sheets call, and how the last call is going. */
data class GroupManage(
    val action: GroupActionState = GroupActionState(),
    /** #199: true when the text was a PromptPay ID (or empty) and was saved. */
    val onSetPromptPay: (String) -> Boolean = { true },
    val onEdit: (name: String, type: GroupType, icon: String) -> Unit = { _, _, _ -> },
    /** [anyway] leaves although changes could not be sent (#218). */
    val onRemove: (anyway: Boolean) -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onAddPeople: (List<NewMember>) -> Unit = {},
    val onRemoveMember: (memberId: String) -> Unit = {},
    /** #218: takes a recorded payment out of the balances, with Undo. */
    val onRemovePayment: (paymentId: String) -> Unit = {},
)

private fun GroupRemoval.label(): String {
    val text = AppText.copy.shared.detail
    return when (this) {
        GroupRemoval.DeleteHere -> text.deleteGroup
        GroupRemoval.DeleteForEveryone -> text.deleteForEveryone
        GroupRemoval.Leave -> text.leaveGroup
    }
}

/** Name and type; the type sets the icon unless the group keeps the one it had. */
@Composable
private fun EditGroupSheet(name: String, type: GroupType, icon: String, onSave: (String, GroupType, String) -> Unit, onDismiss: () -> Unit) {
    val text = LocalCopy.current
    var typed by rememberSaveable { mutableStateOf(name) }
    var picked by rememberSaveable { mutableStateOf(type) }
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(text.shared.detail.editGroup, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        FlowRow(
            modifier = Modifier.padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            GroupType.entries.forEach { option ->
                SkyChip(label = option.label(), selected = option == picked, onClick = { picked = option })
            }
        }
        SharedTextField(value = typed, onValueChange = { typed = it }, label = text.shared.groupName, modifier = Modifier.padding(top = Spacing.md))
        SkyButton(
            text.common.save,
            onClick = { onSave(typed.trim(), picked, if (picked == type) icon else picked.defaultIcon()) },
            enabled = typed.isNotBlank() && (typed.trim() != name || picked != type),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        )
    }
}

/**
 * #199: your own PromptPay ID for this group, so whoever pays you sees a QR. Others see it masked
 * except inside the QR their bank app reads.
 */
@Composable
private fun PromptPaySheet(current: String?, onSave: (String) -> Boolean, onDismiss: () -> Unit) {
    val text = LocalCopy.current
    var typed by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(text.shared.detail.myPromptPay, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        Text(
            if (current == null) text.shared.detail.promptPayAdd else text.shared.detail.promptPayNow(current),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        SharedTextField(
            value = typed,
            onValueChange = {
                typed = it.filter { c -> c.isDigit() || c == '-' || c == ' ' }
                wrong = false
            },
            label = text.shared.detail.promptPayField,
            placeholder = "08x xxx xxxx",
            modifier = Modifier.padding(top = Spacing.md),
        )
        if (wrong) {
            Text(text.shared.detail.promptPayWrong, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.ptut.warnInk, modifier = Modifier.padding(top = Spacing.xs))
        }
        SkyButton(
            if (typed.isBlank() && current != null) text.shared.detail.takeItOff else text.common.save,
            onClick = { wrong = !onSave(typed) },
            enabled = typed.isNotBlank() || current != null,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        )
    }
}

/** #218: names added after Create, from people you know or typed. A shared group's new names join from the link. */
@Composable
private fun AddPeopleSheet(people: List<Person>, onAdd: (List<NewMember>) -> Unit, onDismiss: () -> Unit) {
    val words = LocalCopy.current.shared.detail
    var typed by rememberSaveable { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<NewMember>() }
    val adding = chosen.withTyped(typed)
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(words.addPeople, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        Text(words.addPeopleNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        PeoplePicker(people = people, chosen = chosen, typed = typed, onTyped = { typed = it })
        SkyButton(words.addPeople, onClick = { onAdd(adding) }, enabled = adding.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg))
    }
}

/** #218: the creator archives a name; past splits keep it. Each removal is asked once more. */
@Composable
private fun RemovePeopleSheet(others: List<GroupPersonUiModel>, onRemove: (String) -> Unit, onDismiss: () -> Unit) {
    val text = LocalCopy.current
    val words = text.shared.detail
    var asking by rememberSaveable { mutableStateOf<String?>(null) }
    val person = others.firstOrNull { it.id == asking }
    SkyBottomSheet(onDismissRequest = onDismiss) {
        if (person == null) {
            Text(words.removeSomeone, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
            others.forEach { other ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = SkySizes.transactionRow),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
                ) {
                    MemberAvatar(label = other.name.trim().firstOrNull()?.uppercase() ?: "?", isMe = false)
                    Text(other.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    SkyTextAction(label = text.shared.expense.remove, onClick = { asking = other.id }, enabled = true, ink = MaterialTheme.ptut.warnInk)
                }
            }
        } else {
            Text(words.removeAsk(person.name), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
            Text(words.removeBody(person.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
            if (!person.settled) {
                Text(words.removeUnsettled(person.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.ptut.warnInk, modifier = Modifier.padding(top = Spacing.xs))
            }
            Row(Modifier.fillMaxWidth().padding(top = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                SkyButton(text.common.keepIt, onClick = { asking = null }, kind = SkyButtonKind.Outline, modifier = Modifier.weight(1f))
                SkyButton(
                    text.shared.expense.remove,
                    onClick = {
                        onRemove(person.id)
                        onDismiss()
                    },
                    kind = SkyButtonKind.Danger,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Asked before anything goes. Unsettled balances are said out loud, because they go too. */
@Composable
private fun RemoveGroupSheet(
    name: String,
    removal: GroupRemoval,
    settled: Boolean,
    action: GroupActionState,
    onConfirm: (anyway: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val text = LocalCopy.current
    val words = text.shared.detail
    // #218: Leave stopped because changes could not be sent; the second tap leaves without them.
    val unsent = action.waiting > 0
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(
            when (removal) {
                GroupRemoval.Leave -> words.leaveAsk(name)
                else -> words.deleteAsk(name)
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            when (removal) {
                GroupRemoval.DeleteHere -> words.deleteHereBody
                GroupRemoval.DeleteForEveryone -> words.deleteForEveryoneBody
                GroupRemoval.Leave -> words.leaveBody
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        if (!settled) {
            Text(
                words.notSettled,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.ptut.warnInk,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        if (unsent) {
            Text(words.notSent(action.waiting), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.ptut.warnInk, modifier = Modifier.padding(top = Spacing.xs))
        }
        action.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.xs))
        }
        if (action.working) {
            Row(Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SkyProgress()
                Text(if (removal == GroupRemoval.Leave) words.leaving else words.deleting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            SkyButton(text.common.keepIt, onClick = onDismiss, kind = SkyButtonKind.Outline, modifier = Modifier.weight(1f))
            SkyButton(
                when {
                    unsent -> words.leaveAnyway
                    removal == GroupRemoval.Leave -> words.leave
                    else -> text.common.delete
                },
                onClick = { onConfirm(unsent) },
                kind = SkyButtonKind.Danger,
                enabled = !action.working,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** #190's half of the group screen: whether this phone is signed in, and what the invite buttons do. */
data class GroupInvite(
    /** #141: straight after Create, the invite sheet opens by itself, once. */
    val openOnStart: Boolean = false,
    val signedIn: Boolean = false,
    val action: InviteActionState = InviteActionState(),
    val onInvite: () -> Unit = {},
    val onNewLink: () -> Unit = {},
    val onSwitchOff: () -> Unit = {},
)

private const val STEP_SIGN_IN = "sign-in"
private const val STEP_SIGN_IN_AGAIN = "sign-in-again"
private const val STEP_ADD_PEOPLE = "add-people"
private const val STEP_REMOVE_PEOPLE = "remove-people"
private const val STEP_EDIT = "edit"
private const val STEP_PROMPTPAY = "promptpay"
private const val STEP_REMOVE = "remove"
private const val STEP_SHEET = "sheet"

/** A member who has not joined yet, still only a name. */
private const val NOT_JOINED_ALPHA = 0.6f

@Composable
private fun ExpenseRow(row: GroupFeedRow.Expense, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.transactionRow)
            .tappable(PtutRadii.r12, Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.s20, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupTile(icon = row.icon, ink = true)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.isNew) NewDot()
                Text(row.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
            Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(row.word, style = MaterialTheme.typography.bodySmall, color = row.tone.ink())
            Text(row.amount, style = MaterialTheme.typography.titleMedium, color = row.tone.ink())
        }
    }
}

/** #218: one payment, what it was, and the way to take it back out of the balances. */
@Composable
private fun PaymentSheet(row: GroupFeedRow.Payment, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val text = LocalCopy.current.shared.payment
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(text.detailTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
        Text(row.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.xxs))
        Text(row.amount, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.xs))
        Text(row.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        Text(row.occurred, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(row.recorded, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        SkyButton(text.remove, onClick = onRemove, kind = SkyButtonKind.DangerOutline, modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg))
    }
}

/** #218: asked before a payment leaves the balances; Undo still follows in the toast. */
@Composable
private fun RemovePaymentSheet(shared: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val copy = LocalCopy.current
    val text = copy.shared.payment
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(text.removeAsk, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        Text(
            if (shared) text.removeBodyShared else text.removeBodyLocal,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        SkyButton(text.remove, onClick = onConfirm, kind = SkyButtonKind.Danger, modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg))
        SkyButton(copy.common.keepIt, onClick = onDismiss, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12))
    }
}

/** p27: a settlement sits in the same feed with a neutral tile and no word. Tapping it opens it (#218). */
@Composable
private fun PaymentRow(row: GroupFeedRow.Payment, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.transactionRow)
            .tappable(PtutRadii.r12, Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.s20, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(SkySizes.rowCircle).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
        ) {
            Icon(PtutIcon.Cash.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.rowGlyph))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.isNew) NewDot()
                Text(row.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(row.amount, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** p27: a floating ink add button that keeps the group as context. */
@Composable
private fun AddButton(glass: GlassBackdrop, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = LocalCopy.current.shared.detail.addAnExpense
    GlassSurface(
        backdrop = glass,
        shape = CircleShape,
        fill = MaterialTheme.colorScheme.primary,
        tint = ADD_GLASS_TINT,
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(SkySizes.groupFab)
            .tappable(CircleShape, Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Icon(PtutIcon.Add.image, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(SkySizes.barGlyph))
    }
}

/** Ink glass over the feed: the plus keeps 6.11:1 (day) and 9.67:1 (night) over anything under it. */
private const val ADD_GLASS_TINT = 0.7f

/** A row another member added since the last visit (research finding 5). Ruby: something waits for you. */
@Composable
private fun NewDot() {
    val label = LocalCopy.current.shared.detail.newRow
    Box(
        Modifier
            .padding(end = Spacing.xs)
            .size(SkySizes.newDot)
            .clip(CircleShape)
            .background(MaterialTheme.ptut.signature)
            .semantics { contentDescription = label },
    )
}
