package com.ptut.expense.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SharedArt
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.tappable
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.NewMember
import com.ptut.expense.domain.shared.Person
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.sync.InviteLinks
import com.ptut.expense.ui.sky.SkyBarIconAction
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyChange
import com.ptut.expense.ui.sky.SkyChip
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkyPageSheet
import com.ptut.expense.ui.sky.SkySearchField
import com.ptut.expense.ui.sky.SkySectionHeader
import com.ptut.expense.ui.sky.SkySubChip
import com.ptut.expense.ui.sky.SkyTextAction
import com.ptut.expense.ui.sky.skyItem

/** #136, with #141's ways of making a group: the + choice, New group, and New person. */
@Composable
fun SharedView(
    uiState: SharedUiState,
    onSearch: (String) -> Unit,
    onCreateGroup: (name: String, type: GroupType, icon: String, members: List<NewMember>) -> Unit,
    onAddPerson: (name: String, personId: String?) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenActivity: () -> Unit,
    modifier: Modifier = Modifier,
    /** #190: a code typed by hand, for an invite that came as text rather than a link. */
    onJoinCode: (String) -> Unit = {},
    /** #141: "People >", everyone you split with. */
    onOpenPeople: () -> Unit = {},
) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var startType by rememberSaveable { mutableStateOf(GroupType.Trip) }
    val people = when (uiState) {
        is SharedUiState.Empty -> uiState.people
        is SharedUiState.Ready -> uiState.knownPeople
    }

    when (uiState) {
        is SharedUiState.Empty -> NothingShared(
            onType = {
                startType = it
                sheet = SHEET_GROUP
            },
            onAddPerson = { sheet = SHEET_PERSON },
            onNewGroup = {
                startType = GroupType.Trip
                sheet = SHEET_GROUP
            },
            onJoinCode = { sheet = SHEET_CODE },
            modifier = modifier,
        )

        is SharedUiState.Ready -> Balances(
            state = uiState,
            onSearch = onSearch,
            onAdd = { sheet = SHEET_CHOICE },
            onSignIn = { sheet = SHEET_SIGN_IN },
            onOpenGroup = onOpenGroup,
            onOpenActivity = onOpenActivity,
            onOpenPeople = onOpenPeople,
            modifier = modifier,
        )
    }

    when (sheet) {
        SHEET_CHOICE -> AddChoiceSheet(
            onNewGroup = { sheet = SHEET_GROUP },
            onNewPerson = { sheet = SHEET_PERSON },
            onJoinCode = { sheet = SHEET_CODE },
            onDismiss = { sheet = null },
        )

        SHEET_GROUP -> NewGroupSheet(
            startType = startType,
            people = people,
            onCreate = { name, type, icon, members ->
                sheet = null
                onCreateGroup(name, type, icon, members)
            },
            onDismiss = { sheet = null },
        )

        SHEET_PERSON -> NewPersonSheet(
            people = people,
            onAdd = { name, personId ->
                sheet = null
                onAddPerson(name, personId)
            },
            onDismiss = { sheet = null },
        )

        SHEET_CODE -> JoinCodeSheet(
            onJoin = { code ->
                sheet = null
                onJoinCode(code)
            },
            onDismiss = { sheet = null },
        )

        // The tab's polling syncs as soon as the account is back (#218).
        SHEET_SIGN_IN -> SignInSheet(
            reason = LocalCopy.current.shared.sync.signInAgainReason,
            onSignedIn = { sheet = null },
            onDismiss = { sheet = null },
        )
    }
}

/** p26: the net first, its two halves, then groups on grey and people on a white sheet. */
@Composable
private fun Balances(
    state: SharedUiState.Ready,
    onSearch: (String) -> Unit,
    onAdd: () -> Unit,
    onSignIn: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenActivity: () -> Unit,
    onOpenPeople: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.shared
    var searching by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
    // Painted the sheet's colour, so a short sheet still reaches the bottom; the grey page is drawn
    // only where the group cards and the sheet's rounded top sit.
    LazyColumn(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = Spacing.md)
                    .padding(bottom = Spacing.md),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = SkySizes.topBar),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    SkyBarIconAction(icon = PtutIcon.Search, label = text.home.search, onClick = { searching = !searching })
                    SkyBarIconAction(icon = PtutIcon.GroupAdd, label = text.home.newGroupOrPerson, onClick = onAdd)
                }
                if (searching) {
                    SkySearchField(query = state.query, onSearch = onSearch, placeholder = text.home.search)
                }
                SharedHero(
                    label = state.overallLabel,
                    amount = state.overall,
                    tint = state.tone.ink(),
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
                ) {
                    TotalCard(label = text.home.totalOwed, amount = state.owedToYou, tone = BalanceTone.Owed, modifier = Modifier.weight(1f))
                    TotalCard(label = text.home.totalOwe, amount = state.youOwe, tone = BalanceTone.Owe, filled = true, modifier = Modifier.weight(1f))
                }
                // #196: one quiet line while a change waits; nothing when all is sent.
                SkyChange(target = state.syncLine) { line ->
                    line?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs)) }
                }
                if (state.signedOut) {
                    SkyTextAction(label = text.signIn, onClick = onSignIn, enabled = true, modifier = Modifier.offset(x = -Spacing.xs))
                }
            }
        }
        items(state.groups, key = { it.id }) { group ->
            GroupCard(
                group = group,
                onClick = { onOpenGroup(group.id) },
                modifier = skyItem().background(MaterialTheme.colorScheme.surface).padding(horizontal = Spacing.sm).padding(top = Spacing.s12),
            )
        }
        item {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                SkyPageSheet(modifier = Modifier.padding(top = Spacing.md)) {
                    SkySectionHeader(text = text.people, onClick = onOpenPeople)
                    state.people.forEach { person -> PersonRow(person) }
                    SkyHairline(modifier = Modifier.padding(vertical = Spacing.xs))
                    ActivityLink(label = text.activity, onClick = onOpenActivity)
                    Spacer(Modifier.height(SkySizes.pillClearance))
                }
            }
        }
    }
}

/** The two halves of the net (p26): owed to you on a green tint, what you owe filled ruby (15 Oct). */
@Composable
private fun TotalCard(label: String, amount: String, tone: BalanceTone, modifier: Modifier = Modifier, filled: Boolean = false) {
    val owed = tone == BalanceTone.Owed
    val ink = if (filled) MaterialTheme.ptut.onSignature else tone.ink()
    Column(
        modifier = modifier
            .clip(PtutRadii.r20)
            .background(
                when {
                    filled -> MaterialTheme.ptut.signature
                    owed -> MaterialTheme.ptut.ok.copy(alpha = OWED_TINT)
                    else -> MaterialTheme.colorScheme.surface
                },
            )
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.sm, vertical = Spacing.s14),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ink)
        Text(amount, style = MaterialTheme.typography.titleLarge, color = ink, modifier = Modifier.padding(top = Spacing.xxs))
    }
}

@Composable
private fun GroupCard(group: SharedGroupRowUiModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PtutRadii.r22)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .tappable(PtutRadii.r22, Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupTile(icon = group.icon, ink = group.inkTile)
        Column(Modifier.weight(1f)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(group.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            // What others wrote since this phone last opened the group, in the ruby of "waiting for you".
            group.news?.let { news ->
                Text(news, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.ptut.signatureInk, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(group.word, style = MaterialTheme.typography.bodySmall, color = group.tone.ink())
            Text(group.amount, style = MaterialTheme.typography.titleMedium, color = group.tone.ink())
        }
    }
}

@Composable
private fun PersonRow(person: SharedPersonRowUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = SkySizes.transactionRow).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(label = person.initial, isMe = false)
        Column(Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(person.word, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(person.amount, style = MaterialTheme.typography.titleMedium, color = person.tone.ink())
    }
}

/**
 * Activity's way in, drawn as a row people can see is tappable: a glyph tile, the name and a
 * chevron, on the people rows' edge and height. A bare text action read as a heading.
 */
@Composable
private fun ActivityLink(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.transactionRow)
            .tappable(PtutRadii.r12, Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(SkySizes.rowCircle).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
        ) {
            Icon(PtutIcon.History.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.rowGlyph))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Icon(PtutIcon.ChevronRight.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SkySizes.rowGlyph))
    }
}

/**
 * p34. The art's line "People live only on this phone, and nobody is notified" stopped being true
 * when groups started to sync (13 Oct), so the copy is #136's.
 */
@Composable
private fun NothingShared(
    onType: (GroupType) -> Unit,
    onAddPerson: () -> Unit,
    onNewGroup: () -> Unit,
    onJoinCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.shared
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = SkySizes.topBar),
        ) {
            Text(text.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Box(
            Modifier
                .padding(top = Spacing.sm)
                .fillMaxWidth()
                .aspectRatio(SkySizes.illustrationRatio)
                .clip(PtutRadii.r28)
                .background(MaterialTheme.ptut.barTrack),
        ) { SharedArt() }
        Text(
            text.home.emptyTitle,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text.home.emptyBody,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        FlowRow(
            modifier = Modifier.padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            listOf(GroupType.Trip, GroupType.Flat, GroupType.Couple).forEach { type ->
                SkyChip(label = type.label(), selected = false, onClick = { onType(type) })
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            SkyButton(text.addPerson, onClick = onAddPerson, kind = SkyButtonKind.Outline, modifier = Modifier.weight(1f))
            SkyButton(text.newGroup, onClick = onNewGroup, modifier = Modifier.weight(1.4f))
        }
        SkyTextAction(
            label = text.home.haveInviteCode,
            onClick = onJoinCode,
            enabled = true,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.xs),
        )
        Spacer(Modifier.height(SkySizes.pillClearance))
    }
}

/** #57: the + on the Shared top bar asks which. */
@Composable
private fun AddChoiceSheet(onNewGroup: () -> Unit, onNewPerson: () -> Unit, onJoinCode: () -> Unit, onDismiss: () -> Unit) {
    val text = LocalCopy.current.shared
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(text.home.add, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        SkyButton(text.newGroup, onClick = onNewGroup, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md))
        SkyButton(text.home.newPerson, onClick = onNewPerson, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12))
        SkyButton(text.home.joinWithCode, onClick = onJoinCode, kind = SkyButtonKind.Outline, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12))
    }
}

/** #190: the nine characters from an invite, typed by hand. Spaces and dashes are fine. */
@Composable
private fun JoinCodeSheet(onJoin: (String) -> Unit, onDismiss: () -> Unit) {
    val words = LocalCopy.current.shared.home
    var text by rememberSaveable { mutableStateOf("") }
    val code = InviteLinks.codeIn(text)
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(words.joinWithCode, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        SharedTextField(value = text, onValueChange = { text = it }, label = words.inviteCode, placeholder = "ABC DEF GHJ", modifier = Modifier.padding(top = Spacing.md))
        Text(
            words.orPasteLink,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        SkyButton(
            words.join,
            onClick = { code?.let(onJoin) },
            enabled = code != null,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        )
    }
}

/**
 * #57: name (required) and type, then the people in it. Nothing is stored until Create, and
 * closing the sheet stores nothing.
 */
@Composable
private fun NewGroupSheet(
    startType: GroupType,
    people: List<Person>,
    onCreate: (String, GroupType, String, List<NewMember>) -> Unit,
    onDismiss: () -> Unit,
) {
    val text = LocalCopy.current.shared
    var type by rememberSaveable { mutableStateOf(startType) }
    var name by rememberSaveable { mutableStateOf(startType.label().takeIf { startType != GroupType.Other }.orEmpty()) }
    var typed by rememberSaveable { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<NewMember>() }

    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(text.newGroup, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        FlowRow(
            modifier = Modifier.padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            listOf(GroupType.Trip, GroupType.Flat, GroupType.Couple).forEach { option ->
                SkyChip(
                    label = option.label(),
                    selected = option == type,
                    onClick = {
                        // A name that is still the last chip's word follows the new chip.
                        if (name.isBlank() || name == type.label()) name = option.label()
                        type = option
                    },
                )
            }
        }
        SharedTextField(value = name, onValueChange = { name = it }, label = text.groupName, modifier = Modifier.padding(top = Spacing.md))

        Text(text.people, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.md))
        PeoplePicker(people = people, chosen = chosen, typed = typed, onTyped = { typed = it })
        SkyButton(
            text.home.create,
            // #218: a name typed but not added with Add still joins the group.
            onClick = { onCreate(name.trim(), type, type.defaultIcon(), chosen.withTyped(typed)) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        )
    }
}

/** #57's chips of known people and "Add a name", for New group and for Add people (#218). */
@Composable
internal fun PeoplePicker(people: List<Person>, chosen: SnapshotStateList<NewMember>, typed: String, onTyped: (String) -> Unit) {
    val text = LocalCopy.current.shared
    FlowRow(
        modifier = Modifier.padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        people.forEach { person ->
            val picked = chosen.any { it.personId == person.id }
            SkySubChip(
                label = person.name,
                selected = picked,
                onClick = {
                    if (picked) chosen.removeAll { it.personId == person.id } else chosen += NewMember(person.name, person.id)
                },
            )
        }
        chosen.filter { it.personId == null }.forEach { member ->
            SkySubChip(label = member.name, selected = true, onClick = { chosen.remove(member) })
        }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        SharedTextField(value = typed, onValueChange = onTyped, label = text.home.addAName, modifier = Modifier.weight(1f))
        SkyTextAction(
            label = text.home.add,
            onClick = {
                chosen += NewMember(typed.trim())
                onTyped("")
            },
            enabled = typed.isNotBlank(),
            modifier = Modifier.padding(start = Spacing.s12),
        )
    }
}

/** What was picked, plus a name still in the field. */
internal fun List<NewMember>.withTyped(typed: String): List<NewMember> = toList() + listOfNotNull(typed.trim().takeIf { it.isNotEmpty() }?.let { NewMember(it) })

/** #141: one person for a one-off bill. Behind the scenes it is a group of two, named after them. */
@Composable
internal fun NewPersonSheet(people: List<Person>, onAdd: (String, String?) -> Unit, onDismiss: () -> Unit) {
    val text = LocalCopy.current.shared
    var name by rememberSaveable { mutableStateOf("") }
    var personId by rememberSaveable { mutableStateOf<String?>(null) }
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(text.home.newPerson, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        if (people.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                people.forEach { person ->
                    SkySubChip(
                        label = person.name,
                        selected = person.id == personId,
                        onClick = {
                            personId = person.id
                            name = person.name
                        },
                    )
                }
            }
        }
        SharedTextField(
            value = name,
            onValueChange = {
                name = it
                personId = null
            },
            label = text.home.theirName,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text.home.newPersonNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        SkyButton(
            text.addPerson,
            onClick = { onAdd(name.trim(), personId) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        )
    }
}

private const val SHEET_CHOICE = "choice"
private const val SHEET_GROUP = "group"
private const val SHEET_PERSON = "person"
private const val SHEET_CODE = "code"
private const val SHEET_SIGN_IN = "sign-in"

/** The owed card's green, faint enough that its green text still reads on it. */
private const val OWED_TINT = 0.1f
