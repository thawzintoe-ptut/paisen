package com.ptut.expense.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyNoticeCard
import com.ptut.expense.ui.sky.SkyTextAction

/**
 * First run in Sky (p23): the mark, the illustration slot, "Spending, finally simple.", the
 * offline sentence, then RESTORE beside ALLOW PHOTOS. No sign-in: one user, no accounts.
 *
 * Kept beyond the artboard (#146): "I'll add expenses by hand" under the buttons, because NFR-2's manual
 * path needs no photo access (K21), and the card asking for K PLUS slips in English (C-3, K22).
 * There is no "ไทย" switch: the app is English only in v1 (#147, S6).
 */
@Composable
fun OnboardingView(
    onAllowPhotoAccess: () -> Unit,
    onSkip: () -> Unit,
    onRestore: () -> Unit,
    restoring: Boolean,
    restoreProblem: String?,
    modifier: Modifier = Modifier,
    /** #220: what the chosen file holds, shown before it merges. Null while no file waits. */
    restorePreview: String? = null,
    onConfirmRestore: () -> Unit = {},
    onCancelRestore: () -> Unit = {},
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
            Image(PtutBrandMark, contentDescription = "PaiSeN", modifier = Modifier.padding(top = Spacing.md).size(SkySizes.brandMark))
            Box(
                Modifier
                    .padding(top = Spacing.md)
                    .fillMaxWidth()
                    .aspectRatio(SkySizes.firstRunIllustrationRatio)
                    .clip(PtutRadii.r28)
                    .background(MaterialTheme.ptut.barTrack),
            ) { SlipFlightArt() }
            Text(
                text = text.headline,
                style = MaterialTheme.ptutType.display.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            Row(modifier = Modifier.padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(PtutIcon.ChevronRight.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.cardGlyph))
                Text(
                    text = text.promise,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // C-3: the one setting the reader must change outside this app.
            SkyNoticeCard(
                title = text.settingTitle,
                text = text.settingText,
                icon = PtutIcon.Translate.image,
                modifier = Modifier.padding(top = Spacing.md),
            )
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            SkyButton(label = text.restore, onClick = onRestore, kind = SkyButtonKind.Outline, enabled = !restoring, modifier = Modifier.weight(1f))
            SkyButton(label = text.allowPhotos, onClick = onAllowPhotoAccess, enabled = !restoring, modifier = Modifier.weight(1.6f))
        }
        SkyTextAction(
            label = text.byHand,
            onClick = onSkip,
            enabled = !restoring,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = Spacing.xs),
        )
    }
    restorePreview?.let { holds -> RestorePreviewSheet(holds, working = restoring, onImport = onConfirmRestore, onCancel = onCancelRestore) }
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
