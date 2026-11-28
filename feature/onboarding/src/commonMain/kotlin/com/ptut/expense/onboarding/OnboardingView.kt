package com.ptut.expense.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.ptut.expense.design.PlaySizes
import com.ptut.expense.design.PtutBrandMark
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.folders.DeviceFolder
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyCheckbox
import com.ptut.expense.ui.sky.SkyNoticeCard
import com.ptut.expense.ui.sky.SkyTextAction

/**
 * First run in Sky, in three steps (S01 to S03): what PaiSeN does and the photo ask, the one K PLUS
 * setting, then an optional goal. No sign-in: one user, no accounts. Restore sits on step one as a
 * text link, since Not now took its place on the last step.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OnboardingView(
    state: OnboardingUiState,
    onAllowPhotoAccess: () -> Unit,
    onChooseFolder: (String) -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
    onRestore: () -> Unit,
    restoring: Boolean,
    restoreProblem: String?,
    modifier: Modifier = Modifier,
    /** Null hides Open phone settings, where the platform has no page to open. */
    onOpenSettings: (() -> Unit)? = null,
    /** #220: what the chosen file holds, shown before it merges. Null while no file waits. */
    restorePreview: String? = null,
    onConfirmRestore: () -> Unit = {},
    onCancelRestore: () -> Unit = {},
    onBack: () -> Unit = {},
    onChooseGoal: (GoalPreset) -> Unit = {},
    onGoalAmount: (String) -> Unit = {},
    onStartJar: () -> Unit = {},
) {
    val text = LocalCopy.current.onboarding
    // System Back steps back too; on step one it is left to the system, which closes the app.
    BackHandler(enabled = state.step != OnboardingStep.Promise, onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
        ) {
            // S02's Skip passes the setting and goes on to the goal; the other two open Today.
            StepBar(state, onSkip = if (state.step == OnboardingStep.Slips) onSkip else onFinish)
            when (state.step) {
                OnboardingStep.Promise -> PromiseStep()
                OnboardingStep.Slips -> SlipsStep(state.access, onChooseFolder)
                OnboardingStep.Goal -> GoalStep(state, onChooseGoal, onGoalAmount)
            }
            restoreProblem?.let { problem ->
                Text(
                    text = problem,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.ptut.warnInk,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            Box(Modifier.height(Spacing.md))
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(bottom = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.step) {
                // Allow photos asks, then step two shows the answer; by hand opens Today with no folder.
                OnboardingStep.Promise -> {
                    SkyButton(label = text.allowPhotos, onClick = onAllowPhotoAccess, enabled = !restoring, modifier = Modifier.fillMaxWidth())
                    SkyTextAction(label = text.byHand, onClick = onFinish, enabled = !restoring, modifier = Modifier.padding(top = Spacing.xs))
                    SkyTextAction(label = text.restore, onClick = onRestore, enabled = !restoring, ink = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                OnboardingStep.Slips -> SlipsActions(state.access, onAllowPhotoAccess, onOpenSettings, onSkip, onBack)

                OnboardingStep.Goal -> {
                    SkyButton(label = text.startJar, onClick = onStartJar, enabled = state.canStartJar, modifier = Modifier.fillMaxWidth())
                    SkyTextAction(label = text.notNow, onClick = onFinish, enabled = true, modifier = Modifier.padding(top = Spacing.xs))
                }
            }
        }
    }
    restorePreview?.let { holds -> RestorePreviewSheet(holds, working = restoring, onImport = onConfirmRestore, onCancel = onCancelRestore) }
}

/** `.st-steps`: one segment per step, inked up to this one, and Skip. A screen reader hears "Step N of 3". */
@Composable
private fun StepBar(state: OnboardingUiState, onSkip: () -> Unit) {
    val text = LocalCopy.current.onboarding
    val spoken = text.stepOf(state.stepNumber, OnboardingStep.entries.size)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
            horizontalArrangement = Arrangement.spacedBy(Spacing.s6),
        ) {
            OnboardingStep.entries.forEach { step ->
                val reached = step.ordinal < state.stepNumber
                Box(
                    Modifier
                        .size(SkySizes.stepSegmentWidth, SkySizes.stepSegmentHeight)
                        .clip(CircleShape)
                        .background(if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
                )
            }
        }
        SkyTextAction(label = text.skip, onClick = onSkip, enabled = true)
    }
}

@Composable
private fun StepTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.lg),
    )
}

@Composable
private fun StepText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = Spacing.s12),
    )
}

/** S01: the mark, the headline, then two lines that say what happens and where it stays. */
@Composable
private fun PromiseStep() {
    val text = LocalCopy.current.onboarding
    Image(PtutBrandMark, contentDescription = "PaiSeN", modifier = Modifier.padding(top = Spacing.md).size(SkySizes.brandMark))
    Text(
        text = text.headline,
        style = MaterialTheme.ptutType.display,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.lg),
    )
    PromiseRow(PtutIcon.ReceiptLong, text.promise, Modifier.padding(top = Spacing.lg))
    PromiseRow(PtutIcon.Lock, text.privacy, Modifier.padding(top = Spacing.sm))
    Text(text.usageNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.md))
}

@Composable
private fun PromiseRow(icon: PtutIcon, line: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
        IconCircle(icon)
        Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f).padding(top = Spacing.xs))
    }
}

/** `.sk-row__circle` on a white screen: a grey disc with the glyph in ink. */
@Composable
private fun IconCircle(icon: PtutIcon) {
    Box(
        modifier = Modifier.size(SkySizes.rowCircle).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.rowGlyph))
    }
}

@Composable
private fun SlipsStep(access: SlipAccess, onChooseFolder: (String) -> Unit) {
    val text = LocalCopy.current.onboarding
    when (access) {
        SlipAccess.Unasked -> {
            StepTitle(text.slipsTitle)
            StepText(text.slipsText)
            // Android 14+ offers "Select photos" first; only Allow all lets new slips in unasked.
            SkyNoticeCard(text = text.allowAllHint, icon = PtutIcon.PhotoLibrary.image, modifier = Modifier.padding(top = Spacing.md))
        }

        SlipAccess.Denied -> {
            StepTitle(text.deniedTitle)
            StepText(text.deniedText)
        }

        is SlipAccess.Granted -> {
            StepTitle(text.settingTitle)
            StepText(text.settingText)
            KPlusSetting(Modifier.padding(top = Spacing.s28))
            Text(text.kplusNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
            // #116: v2 only. K PLUS's own folder is watched without asking (FirstRunFolders).
            if (access.folders.isNotEmpty()) {
                Text(text.pickFolder, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.md))
                access.folders.forEach { folder ->
                    FolderRow(folder, chosen = folder.id == access.chosen, onChoose = { onChooseFolder(folder.id) })
                }
            }
        }
    }
}

/** `.st-mock` (S02): the row as K PLUS shows it, so nobody has to guess. One picture to a screen reader. */
@Composable
private fun KPlusSetting(modifier: Modifier = Modifier) {
    val text = LocalCopy.current.onboarding
    val scheme = MaterialTheme.colorScheme
    val spoken = "${text.kplusCard}, ${text.kplusRow}, ${text.kplusSampleValue}"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PtutRadii.r20)
            .background(scheme.surface)
            .padding(Spacing.sm)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Text(text.kplusCard.uppercase(), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Row(
            modifier = Modifier
                .padding(top = Spacing.s10)
                .fillMaxWidth()
                .heightIn(min = SkySizes.fieldRow)
                .clip(PtutRadii.r14)
                .background(scheme.tertiaryContainer)
                .padding(horizontal = Spacing.s14),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PtutIcon.Translate.image, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(SkySizes.rowGlyph))
            Text(text.kplusRow, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.heightIn(min = PlaySizes.token).clip(CircleShape).background(MaterialTheme.ptut.accent).padding(horizontal = Spacing.s10),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text.kplusSampleValue, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                Icon(PtutIcon.Check.image, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(SkySizes.moneyInArrow))
            }
        }
    }
}

@Composable
private fun FolderRow(folder: DeviceFolder, chosen: Boolean, onChoose: () -> Unit) {
    val text = LocalCopy.current.onboarding
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkyCheckbox(checked = chosen, onCheckedChange = { onChoose() }, label = folder.name)
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = folder.imageCount?.takeIf { chosen }?.let(text.foundSlips) ?: folder.path,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SlipsActions(access: SlipAccess, onAllow: () -> Unit, onOpenSettings: (() -> Unit)?, onSkip: () -> Unit, onBack: () -> Unit) {
    val text = LocalCopy.current.onboarding
    when (access) {
        SlipAccess.Unasked -> {
            SkyButton(label = text.allowPhotos, onClick = onAllow, modifier = Modifier.fillMaxWidth())
            SkyTextAction(label = text.byHand, onClick = onSkip, enabled = true, modifier = Modifier.padding(top = Spacing.xs))
        }

        // No board for a refusal: what still works, and three ways on.
        SlipAccess.Denied -> {
            SkyButton(label = text.withoutPhotos, onClick = onSkip, modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                SkyTextAction(label = text.tryAgain, onClick = onAllow, enabled = true)
                onOpenSettings?.let { open -> SkyTextAction(label = text.openSettings, onClick = open, enabled = true) }
            }
        }

        is SlipAccess.Granted -> {
            SkyButton(label = text.doneNext, onClick = onSkip, modifier = Modifier.fillMaxWidth())
            SkyTextAction(label = text.back, onClick = onBack, enabled = true, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
}

/** S03: an optional goal. Nothing saves until Start my jar; Not now and Skip leave no jar. */
@Composable
private fun GoalStep(state: OnboardingUiState, onChoose: (GoalPreset) -> Unit, onAmount: (String) -> Unit) {
    val text = LocalCopy.current.onboarding
    StepTitle(text.goalTitle)
    StepText(text.goalText)
    Column(modifier = Modifier.padding(top = Spacing.md).selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
        GoalPreset.entries.forEach { preset -> GoalOption(preset, picked = preset == state.goal, onPick = { onChoose(preset) }) }
    }
    GoalAmountField(state.goalAmount, onAmount, Modifier.padding(top = Spacing.s20))
}

/** `.pl-option`, as the week challenge draws it: a picked row wears an ink ring. */
@Composable
private fun GoalOption(preset: GoalPreset, picked: Boolean, onPick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PlaySizes.option)
            .semantics { selected = picked }
            .tappable(PtutRadii.r18, Role.RadioButton, onClick = onPick)
            .border(if (picked) PlaySizes.dayRing else SkySizes.hairline, if (picked) scheme.primary else scheme.secondaryContainer, PtutRadii.r18)
            .padding(horizontal = Spacing.sm, vertical = Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s14),
    ) {
        IconCircle(preset.icon())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Text(preset.label(), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(preset.sub(), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        // `.pl-radio`: an outline ring, filled thick in ink once picked.
        Box(
            Modifier
                .size(PlaySizes.radio)
                .border(if (picked) PlaySizes.radioPicked else PlaySizes.dayRing, if (picked) scheme.primary else scheme.onSurfaceVariant, CircleShape),
        )
    }
}

/** S03's "How much?": the label, the home money's symbol, then the number, on one grey row. */
@Composable
private fun GoalAmountField(amount: String, onAmount: (String) -> Unit, modifier: Modifier = Modifier) {
    val text = LocalCopy.current.onboarding
    val scheme = MaterialTheme.colorScheme
    val number = MaterialTheme.typography.titleMedium.copy(color = scheme.onSurface, textAlign = TextAlign.End)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SkySizes.fieldRow)
            .clip(PtutRadii.r16)
            .background(scheme.surface)
            .padding(horizontal = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.goalAmount, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(Home.money.symbol, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        BasicTextField(
            value = amount,
            onValueChange = onAmount,
            singleLine = true,
            textStyle = number,
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(SkySizes.goalAmountField).semantics { contentDescription = text.goalAmount },
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (amount.isEmpty()) Text("0", style = number, color = scheme.onSurfaceVariant)
                    field()
                }
            },
        )
    }
}

@Composable
private fun GoalPreset.label(): String = LocalCopy.current.onboarding.let { text ->
    when (this) {
        GoalPreset.Trip -> text.goalTrip
        GoalPreset.SomethingNew -> text.goalNew
        GoalPreset.Cushion -> text.goalCushion
    }
}

@Composable
private fun GoalPreset.sub(): String = LocalCopy.current.onboarding.let { text ->
    when (this) {
        GoalPreset.Trip -> text.goalTripSub
        GoalPreset.SomethingNew -> text.goalNewSub
        GoalPreset.Cushion -> text.goalCushionSub
    }
}

private fun GoalPreset.icon(): PtutIcon = when (this) {
    GoalPreset.Trip -> PtutIcon.Work
    GoalPreset.SomethingNew -> PtutIcon.Smartphone
    GoalPreset.Cushion -> PtutIcon.VerifiedUser
}

/** #220: the same question Import my data asks, with its words, before a first-run restore merges. */
@Composable
private fun RestorePreviewSheet(holds: String, working: Boolean, onImport: () -> Unit, onCancel: () -> Unit) {
    val copy = LocalCopy.current
    SkyBottomSheet(onDismissRequest = onCancel) {
        Text(copy.settings.inThisFile, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        Text(holds, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        SkyButton(label = copy.settings.importLedger, onClick = onImport, enabled = !working, modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg))
        SkyButton(label = copy.common.cancel, onClick = onCancel, kind = SkyButtonKind.Outline, enabled = !working, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12))
    }
}
