package com.ptut.expense.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyCheckbox
import com.ptut.expense.ui.sky.SkyDashedAddRow
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkyNoticeCard
import com.ptut.expense.ui.sky.SkyTopBar

/**
 * FR-5 in Sky (p22): "Where your slips are", one row per gallery folder with its path and image
 * count and a checkbox, and SAVE · N FOLDERS. A tick changes nothing until SAVE (#169).
 *
 * Kept beyond the artboard (#146, K24): each folder's honest state ("Not checked", "No slips found yet",
 * "N slips read"), the summary, the no-access notice with the platform's own action, folders the
 * phone no longer offers, and the footnote on what is read.
 */
@Composable
fun PhotoFoldersView(
    uiState: PhotoFoldersUiState,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onSave: () -> Unit,
    onChooseAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.folders
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .navigationBarsPadding(),
    ) {
        SkyTopBar(title = text.title, onBack = onBack)
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Column(modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.xs, bottom = Spacing.sm)) {
                    Text(
                        text = text.headline,
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = text.intro,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    Text(
                        text = uiState.summary,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }

            if (!uiState.accessGranted) {
                item {
                    SkyNoticeCard(
                        text = text.accessOff,
                        icon = PtutIcon.Lock.image,
                        modifier = Modifier.padding(horizontal = Spacing.md).padding(bottom = Spacing.sm),
                    )
                }
            }

            items(uiState.rows, key = { it.id }) { row ->
                FolderRow(row = row, onToggle = { onToggle(row.id) })
            }

            uiState.chooseMore?.let { label ->
                item {
                    Column(modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md)) {
                        // iOS only: Android lists every folder already, so it has nothing to add (#125).
                        SkyDashedAddRow(label = label, onClick = onChooseAnother, modifier = Modifier.fillMaxWidth())
                        Text(
                            text = uiState.chooseMoreExplanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }

            item {
                Text(
                    text = text.footnote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md, bottom = Spacing.md),
                )
            }
        }
        SkyButton(
            label = text.save(uiState.ticked),
            onClick = onSave,
            enabled = uiState.canSave,
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        )
    }
}

@Composable
private fun FolderRow(row: FolderRowUiModel, onToggle: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SkySizes.transactionRow)
                .semantics(mergeDescendants = true) { stateDescription = row.reading }
                .tappable(PtutRadii.r12, Role.Checkbox, onClick = onToggle)
                .padding(vertical = Spacing.s12),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(SkySizes.rowCircle).clip(PtutRadii.r12).background(MaterialTheme.colorScheme.surface),
            ) {
                Icon(PtutIcon.Folder.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(SkySizes.rowGlyph))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.ptutType.listHeadline, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = listOfNotNull(row.path, row.images).joinToString(" · "),
                    style = MaterialTheme.ptutType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The folder's own state, honest about a folder never checked (K24).
                Text(
                    text = row.reading,
                    style = MaterialTheme.ptutType.meta,
                    color = if (row.badged) MaterialTheme.ptut.ok else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SkyCheckbox(checked = row.ticked, onCheckedChange = null)
        }
        SkyHairline()
    }
}
