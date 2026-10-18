package com.ptut.expense.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.categoryTint
import com.ptut.expense.design.ptutIcon
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyBarIconAction
import com.ptut.expense.ui.sky.SkyDashedAddRow
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkyTextAction
import com.ptut.expense.ui.sky.SkyTopBar

/**
 * FR-12 and FR-13 in Sky (p19): "+" in the bar, one line on what a sub-category does, then a row
 * per category with its sub-categories and this month's spend, and a dashed NEW CATEGORY.
 *
 * The order drives every chip list, so reordering stays (#146, K17): a Reorder action swaps each
 * row's chevron for a pair of arrows until Done.
 */
@Composable
fun CategoriesView(
    uiState: CategoriesUiState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onMove: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var reordering by rememberSaveable { mutableStateOf(false) }
    val copy = LocalCopy.current
    val text = copy.categories
    LazyColumn(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) {
        item {
            SkyTopBar(
                title = text.title,
                onBack = onBack,
                trailing = { SkyBarIconAction(icon = PtutIcon.Add, label = text.newCategory, onClick = onNew) },
            )
        }
        item {
            Row(
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text.intro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                SkyTextAction(
                    label = if (reordering) copy.common.done else text.reorder,
                    onClick = { reordering = !reordering },
                    enabled = uiState.rows.size > 1,
                )
            }
        }

        items(uiState.rows, key = { it.id }) { row ->
            CategoryRow(
                row = row,
                reordering = reordering,
                onClick = { onOpen(row.id) },
                onMove = { by -> onMove(row.id, by) },
            )
        }

        item {
            SkyDashedAddRow(
                label = text.newCategory,
                onClick = onNew,
                modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md),
            )
        }
        item { Box(Modifier.height(SkySizes.pillClearance)) }
    }
}

@Composable
private fun CategoryRow(
    row: CategoryRowUiModel,
    reordering: Boolean,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val tint = categoryTint(row.color)
    val type = MaterialTheme.ptutType
    val text = LocalCopy.current.categories
    Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SkySizes.transactionRow)
                .tappable(PtutRadii.r12, Role.Button, enabled = !reordering, onClick = onClick)
                .padding(vertical = Spacing.s12),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(SkySizes.rowCircle).clip(CircleShape).background(tint.fill),
            ) {
                Icon(ptutIcon(row.icon), contentDescription = null, tint = tint.on, modifier = Modifier.size(SkySizes.rowGlyph))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, style = type.listHeadline, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = row.subCategories.joinToString(" · ").ifEmpty { text.noSubCategories },
                    style = type.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (reordering) {
                MoveArrow(PtutIcon.ChevronUp, text.moveUp(row.name), row.canMoveUp) { onMove(-1) }
                MoveArrow(PtutIcon.ChevronDown, text.moveDown(row.name), row.canMoveDown) { onMove(1) }
            } else {
                Text(row.spentThisMonth, style = type.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(
                    imageVector = PtutIcon.ChevronRight.image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.settingsTrailing),
                )
            }
        }
        SkyHairline()
    }
}

/** The end of the list draws the arrow disabled rather than dropping it, so the row does not shift. */
@Composable
private fun MoveArrow(icon: PtutIcon, label: String, enabled: Boolean, onClick: () -> Unit) {
    if (enabled) {
        SkyBarIconAction(icon = icon, label = label, onClick = onClick)
    } else {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(Sizes.minTouchTarget)) {
            Icon(icon.image, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(SkySizes.barGlyph))
        }
    }
}
