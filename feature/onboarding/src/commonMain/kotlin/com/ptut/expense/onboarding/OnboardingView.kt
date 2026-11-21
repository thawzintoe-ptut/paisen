package com.ptut.expense.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.ptut.expense.design.PtutBrandMark
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.SlipFlightArt
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutType
import com.ptut.expense.domain.Home
import com.ptut.expense.domain.folders.DeviceFolder
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyCheckbox
import com.ptut.expense.ui.sky.SkyChip
import com.ptut.expense.ui.sky.SkyEditableKeyValueRow
import com.ptut.expense.ui.sky.SkyKeyValueRow
import com.ptut.expense.ui.sky.SkyNoticeCard
import com.ptut.expense.ui.sky.SkyTextAction

/**
 * First run in Sky (p23), in three steps (#102): what PaiSeN does with slips, the slip folder, then
 * an optional goal. No sign-in: one user, no accounts. RESTORE sits on the last step as a text link.
 */
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
            StepBar(state, onBack = onBack, onSkip = onFinish)
            Image(PtutBrandMark, contentDescription = "PaiSeN", modifier = Modifier.padding(top = Spacing.xs).size(SkySizes.brandMark))
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
        Box(Modifier.padding(horizontal = Spacing.md).fillMaxWidth().height(SkySizes.hairline).background(MaterialTheme.colorScheme.onSurface))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.step) {
                // Allow photos asks, then step two shows the answer; by hand opens Today with no folder.
                OnboardingStep.Promise -> {
                    SkyButton(label = text.allowPhotos, onClick = onAllowPhotoAccess, modifier = Modifier.fillMaxWidth())
                    SkyTextAction(label = text.byHand, onClick = onFinish, enabled = true, modifier = Modifier.padding(vertical = Spacing.xs))
                }

                OnboardingStep.Slips -> SlipsActions(state.access, onAllowPhotoAccess, onOpenSettings, onSkip)

                OnboardingStep.Goal -> {
                    SkyButton(label = text.startJar, onClick = onStartJar, enabled = state.canStartJar && !restoring, modifier = Modifier.fillMaxWidth())
                    SkyTextAction(label = text.restore, onClick = onRestore, enabled = !restoring, modifier = Modifier.padding(vertical = Spacing.xs))
                }
            }
        }
    }
    restorePreview?.let { holds -> RestorePreviewSheet(holds, working = restoring, onImport = onConfirmRestore, onCancel = onCancelRestore) }
}

/** #102: "Step N of 3", Back from step two on, and Skip, which opens Today with nothing more asked. */
@Composable
private fun StepBar(state: OnboardingUiState, onBack: () -> Unit, onSkip: () -> Unit) {
    val text = LocalCopy.current.onboarding
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (state.step != OnboardingStep.Promise) SkyTextAction(label = text.back, onClick = onBack, enabled = true)
        }
        Text(
            text = text.stepOf(state.stepNumber, OnboardingStep.entries.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            SkyTextAction(label = text.skip, onClick = onSkip, enabled = true)
        }
    }
}

@Composable
private fun StepTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.ptutType.display.copy(fontWeight = FontWeight.Light),
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
        modifier = modifier.padding(top = Spacing.md),
    )
}

@Composable
private fun PromiseStep() {
    val text = LocalCopy.current.onboarding
    Box(
        Modifier
            .padding(top = Spacing.md)
            .fillMaxWidth()
            .aspectRatio(SkySizes.firstRunIllustrationRatio)
            .clip(PtutRadii.r28)
            .background(MaterialTheme.ptut.barTrack),
    ) { SlipFlightArt() }
    StepTitle(text.headline)
    StepText(text.promise)
    // #102: why photos, before the button that asks for them.
    StepText(text.slipsText)
    SkyNoticeCard(text = text.privacy, icon = PtutIcon.Lock.image, modifier = Modifier.padding(top = Spacing.md))
    // Android 14+ offers "Select photos" first; only Allow all lets new slips in unasked.
    SkyNoticeCard(text = text.allowAllHint, icon = PtutIcon.PhotoLibrary.image, modifier = Modifier.padding(top = Spacing.s12))
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
            StepTitle(text.pickFolder)
            if (access.folders.isEmpty()) StepText(text.noFolders)
            access.folders.forEach { folder ->
                FolderRow(folder, chosen = folder.id == access.chosen, onChoose = { onChooseFolder(folder.id) })
            }
            // C-3: the one bank setting the reader needs, as the row K PLUS itself shows.
            StepText(text.settingText)
            SkyKeyValueRow(label = text.kplusSample, value = text.kplusSampleValue, modifier = Modifier.padding(top = Spacing.xs))
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
private fun SlipsActions(access: SlipAccess, onAllow: () -> Unit, onOpenSettings: (() -> Unit)?, onSkip: () -> Unit) {
    val text = LocalCopy.current.onboarding
    when (access) {
        SlipAccess.Unasked -> {
            SkyButton(label = text.allowPhotos, onClick = onAllow, modifier = Modifier.fillMaxWidth())
            SkyTextAction(label = text.byHand, onClick = onSkip, enabled = true, modifier = Modifier.padding(vertical = Spacing.xs))
        }

        SlipAccess.Denied -> {
            SkyButton(label = text.withoutPhotos, onClick = onSkip, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                SkyTextAction(label = text.tryAgain, onClick = onAllow, enabled = true, modifier = Modifier.padding(vertical = Spacing.xs))
                onOpenSettings?.let { open -> SkyTextAction(label = text.openSettings, onClick = open, enabled = true, modifier = Modifier.padding(vertical = Spacing.xs)) }
            }
        }

        is SlipAccess.Granted -> SkyButton(label = text.next, onClick = onSkip, modifier = Modifier.fillMaxWidth())
    }
}

/** #102: an optional goal. Nothing saves until Start my jar; Skip leaves no jar. */
@Composable
private fun GoalStep(state: OnboardingUiState, onChoose: (GoalPreset) -> Unit, onAmount: (String) -> Unit) {
    val text = LocalCopy.current.onboarding
    StepTitle(text.goalTitle)
    StepText(text.goalText)
    FlowRow(
        modifier = Modifier.padding(top = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        GoalPreset.entries.forEach { preset ->
            SkyChip(label = preset.label(), selected = preset == state.goal, onClick = { onChoose(preset) })
        }
    }
    SkyEditableKeyValueRow(
        label = "${text.goalAmount} ${Home.money.symbol}",
        value = state.goalAmount,
        onValueChange = onAmount,
        placeholder = "0",
        modifier = Modifier.padding(top = Spacing.sm),
    )
    StepText(text.goalNoMoney)
}

@Composable
private fun GoalPreset.label(): String = LocalCopy.current.onboarding.let { text ->
    when (this) {
        GoalPreset.Trip -> text.goalTrip
        GoalPreset.SomethingNew -> text.goalNew
        GoalPreset.Cushion -> text.goalCushion
    }
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
