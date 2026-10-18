package com.ptut.expense.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import com.ptut.expense.design.CategoryIcon
import com.ptut.expense.design.CategoryTint
import com.ptut.expense.design.CategoryTintColors
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.categoryTint
import com.ptut.expense.design.ptutIcon
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable
import com.ptut.expense.domain.Direction
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.AskBeforeLeaving
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyConfirmAction
import com.ptut.expense.ui.sky.SkyDiscardSheet
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkyNoticeCard
import com.ptut.expense.ui.sky.SkySegmentedToggle
import com.ptut.expense.ui.sky.SkyTopBar

/**
 * Edit category in Sky (p20): the glyph in its circle, the name on an ink rule, a row of six
 * glyphs and More, the sub-categories with a remove each, "+ Add sub-category", and DELETE
 * CATEGORY outlined in red with what it will ask.
 *
 * Kept beyond the artboard (#146): the colour (K18), Money out / Money in (K19), inline rename that keeps
 * the sub-category's id (K20), the keep-spending notice, the "Where should these go?" question,
 * the last-category rule, the blank-name Save rule and the screen-reader labels. The six inline
 * glyphs are picked by hand, because no usage data says which are used most (#147, S10).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditCategoryView(
    uiState: EditCategoryUiState,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onIcon: (String) -> Unit,
    onDirection: (Direction) -> Unit,
    onColor: (String) -> Unit,
    onSave: () -> Unit,
    onAddSubCategory: (String) -> Unit,
    onRenameSubCategory: (String, String) -> Unit,
    onRemoveSubCategory: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var asking by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var colouring by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }
    // Held here so Save, Done and leaving all see a sub-category typed but not yet ticked (#214).
    var newSub by rememberSaveable { mutableStateOf<String?>(null) }
    val typedSub = newSub?.trim().orEmpty()
    val changed = uiState.edited || typedSub.isNotEmpty()
    val addTyped: () -> Unit = {
        if (typedSub.isNotEmpty()) onAddSubCategory(typedSub)
        newSub = null
    }
    val leave: () -> Unit = { if (changed) discarding = true else onBack() }
    val text = LocalCopy.current.categories

    AskBeforeLeaving(changed = changed) { discarding = true }

    LazyColumn(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) {
        item {
            // A close, not an arrow: this is a form, and leaving without saving is no step back.
            SkyTopBar(
                title = text.editTitle,
                onBack = leave,
                backIcon = PtutIcon.Close,
                backLabel = text.closeWithoutSaving,
                trailing = {
                    SkyConfirmAction(
                        enabled = uiState.dirty || typedSub.isNotEmpty(),
                        onClick = {
                            addTyped()
                            onSave()
                        },
                    )
                },
            )
        }

        // Nothing below can be drawn from a category that has not arrived.
        if (!uiState.loaded) return@LazyColumn

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val tint = categoryTint(uiState.color)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .size(IDENTITY_CIRCLE)
                        .clip(CircleShape)
                        .background(tint.fill)
                        .semantics { contentDescription = text.changeIcon }
                        .tappable(CircleShape, Role.Button) { picking = true },
                ) {
                    Icon(ptutIcon(uiState.icon), contentDescription = null, tint = tint.on, modifier = Modifier.size(Sizes.identityTileGlyph))
                }
                NameField(stored = uiState.name, onRename = onRename)
                InlineIcons(
                    selected = uiState.icon,
                    onIcon = onIcon,
                    onMore = { picking = true },
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md)) {
                ColourRow(selected = uiState.color, onOpen = { colouring = true })
                // Not on the artboard: a category the reader made has to say which way the money goes.
                SkySegmentedToggle(
                    options = listOf(text.moneyOut, text.moneyIn),
                    selected = if (uiState.direction == Direction.Income) 1 else 0,
                    onSelect = { onDirection(if (it == 1) Direction.Income else Direction.Expense) },
                    modifier = Modifier.padding(top = Spacing.s12),
                )
                Text(
                    text = text.directionHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
                Text(
                    text = text.subCategories,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xxs),
                )
            }
        }

        items(uiState.subCategories, key = { it.id }) { sub ->
            SubCategoryRow(
                row = sub,
                onRename = { name -> onRenameSubCategory(sub.id, name) },
                onRemove = { onRemoveSubCategory(sub.id) },
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }

        item {
            AddSubCategory(
                typed = newSub,
                onType = { newSub = it },
                onAdd = addTyped,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }

        item {
            SkyNoticeCard(
                text = text.removingKeepsSpending(uiState.name),
                icon = PtutIcon.Info.image,
                modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md),
            )
        }

        if (uiState.canDelete) {
            item {
                SkyButton(
                    label = text.deleteCategory,
                    onClick = { asking = true },
                    kind = SkyButtonKind.DangerOutline,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.lg),
                )
            }
            item {
                Text(
                    text = uiState.deleteConsequence,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.s12),
                )
            }
        }
        item { Box(Modifier.height(SkySizes.pillClearance)) }
    }

    if (picking) {
        ChooseIconSheet(
            selected = uiState.icon,
            onDismiss = { picking = false },
            onChoose = { chosen ->
                picking = false
                onIcon(chosen)
            },
        )
    }

    if (colouring) {
        ChooseColourSheet(
            selected = uiState.color,
            onDismiss = { colouring = false },
            onChoose = { chosen ->
                colouring = false
                onColor(chosen)
            },
        )
    }

    if (discarding) {
        SkyDiscardSheet(
            onKeep = { discarding = false },
            onDiscard = {
                discarding = false
                onBack()
            },
        )
    }

    if (asking) {
        WhereDoTheyGoSheet(
            destinations = uiState.deleteDestinations,
            consequence = uiState.deleteConsequence,
            onDismiss = { asking = false },
            onChoose = { destination ->
                asking = false
                onDelete(destination)
            },
        )
    }
}

/**
 * The name, centred on an ink rule (p20) and held here rather than read back from the ViewModel:
 * that copy returns through a `combine` a frame behind each key, which would move the caret.
 */
@Composable
private fun NameField(stored: String, onRename: (String) -> Unit) {
    // Saveable, so a rotation keeps both the text and where the caret was in it.
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(stored, TextRange(stored.length)))
    }
    val label = LocalCopy.current.categories.categoryName
    Column(modifier = Modifier.padding(top = Spacing.sm).width(NAME_RULE), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicTextField(
            value = draft,
            onValueChange = {
                draft = it
                onRename(it.text)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
        Box(Modifier.padding(top = Spacing.xxs).fillMaxWidth().height(SkySizes.hairline).background(MaterialTheme.colorScheme.onSurface))
    }
}

/** Six glyphs a category most plausibly means, then More for the full picker (#147, S10). */
@Composable
private fun InlineIcons(selected: String, onIcon: (String) -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val copy = LocalCopy.current
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        INLINE_ICONS.forEach { icon ->
            IconTileChoice(
                icon = icon,
                label = copy.shell.iconNames[icon.designName] ?: icon.designName.replace('_', ' '),
                chosen = icon.designName == selected,
                onClick = { onIcon(icon.designName) },
                modifier = Modifier.weight(1f),
            )
        }
        IconTileChoice(icon = PtutIcon.MoreHoriz, label = copy.categories.moreIcons, chosen = false, onClick = onMore, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun IconTileChoice(icon: PtutIcon, label: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .heightIn(min = Sizes.minTouchTarget)
            .clip(PtutRadii.r14)
            .background(if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .semantics {
                contentDescription = label
                selected = chosen
            }
            .tappable(PtutRadii.r14, Role.Button, onClick = onClick),
    ) {
        Icon(
            imageVector = icon.image,
            contentDescription = null,
            tint = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(SkySizes.rowGlyph),
        )
    }
}

/** The colour, a row of its own: the circle above cannot carry two edit affordances (K18). */
@Composable
private fun ColourRow(selected: String, onOpen: () -> Unit) {
    val tint = categoryTint(selected)
    val text = LocalCopy.current.categories
    val named = CategoryTint.named(selected)
    Column {
        SkyHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SkySizes.keyValueRow)
                .tappable(PtutRadii.r12, Role.Button, onClick = onOpen)
                .padding(vertical = Spacing.s12),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text.colour, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Box(Modifier.size(Spacing.md).clip(CircleShape).background(tint.fill))
            Text(text.colourNames[named.storedName] ?: named.spoken, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Icon(PtutIcon.ChevronRight.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.settingsTrailing))
        }
        SkyHairline()
    }
}

@Composable
private fun SubCategoryRow(
    row: SubCategoryRowUiModel,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Held here, not read back from the ViewModel, for the reason [NameField] gives.
    var draft by rememberSaveable(row.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(row.name, TextRange(row.name.length)))
    }
    val text = LocalCopy.current.categories
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = SkySizes.keyValueRow),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A field rather than a label: renaming by delete-and-retype loses the id (K20).
            BasicTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    onRename(it.text)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).semantics { contentDescription = text.renameSub(row.name) },
            )
            Text(row.purchases, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(Sizes.minTouchTarget)
                    .semantics { contentDescription = text.removeSub(row.name) }
                    .tappable(CircleShape, Role.Button, onClick = onRemove),
            ) {
                Icon(PtutIcon.Close.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.settingsTrailing))
            }
        }
        SkyHairline()
    }
}

/**
 * "+ Add sub-category" (p20), which becomes the row it is about to be once tapped. Null [typed]
 * is the closed row; the tick and the keyboard's Done both add what was typed (#214).
 */
@Composable
private fun AddSubCategory(typed: String?, onType: (String?) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val naming = typed
    val text = LocalCopy.current.categories
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.keyValueRow)
            .then(if (naming == null) Modifier.tappable(PtutRadii.r12, Role.Button) { onType("") } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (naming == null) {
            Icon(PtutIcon.Add.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(Sizes.settingsTrailing))
            Text(text.addSubCategory, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        } else {
            BasicTextField(
                value = naming,
                onValueChange = onType,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
                modifier = Modifier.weight(1f).semantics { contentDescription = text.nameTheSub },
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(Sizes.minTouchTarget)
                    .semantics { contentDescription = text.addThisSub }
                    .tappable(CircleShape, Role.Button, enabled = naming.isNotBlank(), onClick = onAdd),
            ) {
                Icon(PtutIcon.Check.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(Sizes.settingsTrailing))
            }
        }
    }
}

/** p20 draws cart, cake, truck, bolt, bag and banknotes. */
private val INLINE_ICONS = listOf(
    PtutIcon.LocalGroceryStore,
    PtutIcon.Restaurant,
    PtutIcon.DirectionsBus,
    PtutIcon.Bolt,
    PtutIcon.ShoppingBag,
    PtutIcon.Cash,
)

private val IDENTITY_CIRCLE = SkySizes.rowCircle * 1.8f

/** p20's ink rule under the name is about half the screen wide. */
private val NAME_RULE = SkySizes.rowCircle * 5

/**
 * The shape of the three questions this screen asks (#179), the same as the record's delete sheet
 * (#162): a light title, the choices, then an outline button that leaves things as they are.
 */
@Composable
private fun ChoiceSheet(
    title: String,
    keepLabel: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md),
        )
        content()
        SkyButton(
            label = keepLabel,
            onClick = onDismiss,
            kind = SkyButtonKind.Outline,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        )
    }
}

/**
 * Seven swatches and no wheel. Neither alert red nor sky is here — see [CategoryTint] — and a
 * wheel would put both back within two drags.
 */
@Composable
private fun ChooseColourSheet(selected: String, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    val chosen = CategoryTint.named(selected)
    val text = LocalCopy.current.categories

    ChoiceSheet(title = text.chooseColour, keepLabel = text.keepThisOne, onDismiss = onDismiss) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(top = Spacing.md),
        ) {
            CategoryTint.entries.forEach { tint ->
                ColourChoice(
                    tint = tint,
                    colors = categoryTint(tint),
                    chosen = tint == chosen,
                    onClick = { onChoose(tint.storedName) },
                )
            }
        }
    }
}

/** A circle, as a category wears it. The chosen one carries a tick, because colour alone cannot say which is on. */
@Composable
private fun ColourChoice(
    tint: CategoryTint,
    colors: CategoryTintColors,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    val spoken = LocalCopy.current.categories.colourNames[tint.storedName] ?: tint.spoken
    Box(
        modifier = Modifier
            .size(Sizes.touchTarget)
            .tappable(CircleShape, Role.Button, onClick = onClick)
            .background(colors.fill, CircleShape)
            .semantics {
                contentDescription = spoken
                selected = chosen
            },
        contentAlignment = Alignment.Center,
    ) {
        if (chosen) {
            Icon(
                imageVector = PtutIcon.Check.image,
                contentDescription = null,
                tint = colors.on,
                modifier = Modifier.size(Sizes.settingsGlyph),
            )
        }
    }
}

/**
 * FR-12's "re-iconed", for the glyphs beyond the six inline tiles. The glyphs on offer are the
 * ones a category can plausibly mean, not the whole drawing table — see [PtutIcon.categoryChoices].
 *
 * Choosing closes the sheet and marks the screen dirty. Nothing is written until Save, which is
 * the same bargain the name field makes, so one Save still covers the whole screen.
 */
@Composable
private fun ChooseIconSheet(
    selected: String,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    val text = LocalCopy.current.categories
    ChoiceSheet(title = text.chooseIcon, keepLabel = text.keepThisOne, onDismiss = onDismiss) {
        // Forty do not fit one screen, and a grid that stops at the fold is a grid whose last
        // row nobody knows is there.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier
                .padding(top = Spacing.md)
                .heightIn(max = Sizes.pickerHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            PtutIcon.categoryChoices.forEach { choice ->
                IconChoice(
                    choice = choice,
                    chosen = choice.icon.designName == selected,
                    onClick = { onChoose(choice.icon.designName) },
                )
            }
        }
    }
}

/** Drawn as the inline tiles are: the one already worn in ink, so the grid opens saying which it is. */
@Composable
private fun IconChoice(choice: CategoryIcon, chosen: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Sizes.touchTarget)
            .clip(PtutRadii.r14)
            .background(if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .semantics {
                contentDescription = choice.spoken
                selected = chosen
            }
            .tappable(PtutRadii.r14, Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = choice.icon.image,
            contentDescription = null,
            tint = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(SkySizes.rowGlyph),
        )
    }
}

/**
 * FR-18: the question is asked before anything is deleted, and it has no "just delete" answer.
 * Keeping the category is the only way to leave without choosing.
 */
@Composable
private fun WhereDoTheyGoSheet(
    destinations: List<CategoryChoiceUiModel>,
    consequence: String,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    val text = LocalCopy.current.categories
    ChoiceSheet(title = text.whereShouldTheseGo, keepLabel = text.keepTheCategory, onDismiss = onDismiss) {
        Text(
            text = consequence,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Column(
            modifier = Modifier
                .padding(top = Spacing.sm)
                .heightIn(max = Sizes.pickerHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            destinations.forEach { destination ->
                DestinationRow(destination = destination, onClick = { onChoose(destination.id) })
            }
        }
    }
}

/** A place for the rows to go, drawn as the category it is: its tint circle and its name. */
@Composable
private fun DestinationRow(destination: CategoryChoiceUiModel, onClick: () -> Unit) {
    val tint = categoryTint(destination.color)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SkySizes.transactionRow)
                .tappable(PtutRadii.r12, Role.Button, onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(SkySizes.rowCircle).clip(CircleShape).background(tint.fill),
            ) {
                Icon(ptutIcon(destination.icon), contentDescription = null, tint = tint.on, modifier = Modifier.size(SkySizes.rowGlyph))
            }
            Text(
                text = destination.name,
                style = MaterialTheme.ptutType.listHeadline,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        SkyHairline()
    }
}
