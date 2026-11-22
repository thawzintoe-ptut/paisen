package com.ptut.expense.ui.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptutIcon
import com.ptut.expense.domain.Category
import com.ptut.expense.domain.categories.CategoryChips
import com.ptut.expense.domain.categories.keepingOrder
import com.ptut.expense.domain.categories.sheetPickChanges
import com.ptut.expense.locale.LocalCopy

/**
 * Hick's law: [chips] shows three categories, then a More chip that opens every one of [all] in a
 * sheet. A tap keeps the chips where they are; a sheet pick not shown takes the last place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChipRow(
    chips: CategoryChips,
    all: List<Category>,
    selectedId: String?,
    onPick: (Category) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = SkySizes.chip,
    icons: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    // Frozen on the first tap, so usage arriving after open can still order the row.
    var order by rememberSaveable(all.map { it.id }) { mutableStateOf<List<String>?>(null) }
    val steady = chips.keepingOrder(order, all, selectedId)
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        steady.shown.forEach { category ->
            SkyChip(
                label = category.name,
                selected = category.id == selectedId,
                onClick = {
                    order = steady.shown.map { it.id }
                    onPick(category)
                },
                height = height,
                icon = if (icons) ptutIcon(category.icon) else null,
            )
        }
        if (steady.hasMore) MoreChip(count = steady.moreCount, height = height, onClick = { open = true })
    }
    if (open) {
        AllCategoriesSheet(
            all = all,
            selectedId = selectedId,
            icons = icons,
            onPick = {
                order = steady.shown.map { shown -> shown.id }
                if (sheetPickChanges(it, selectedId)) onPick(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

/** "More ›" in the row's own chip shape; a screen reader hears how many wait behind it. */
@Composable
private fun MoreChip(count: Int, height: Dp, onClick: () -> Unit) {
    val common = LocalCopy.current.common
    val scheme = MaterialTheme.colorScheme
    SkyChipTarget(
        selected = false,
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = common.moreCategories(count) },
    ) { press ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = height)
                .clip(CircleShape)
                .then(press)
                .background(scheme.tertiaryContainer)
                .border(SkySizes.chipBorder, scheme.surfaceContainerHigh, CircleShape)
                .padding(start = Spacing.s14, end = Spacing.xs),
        ) {
            Text(text = common.more, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 1)
            Icon(PtutIcon.ChevronRight.image, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(SkySizes.chipGlyph))
        }
    }
}

@Composable
private fun AllCategoriesSheet(all: List<Category>, selectedId: String?, icons: Boolean, onPick: (Category) -> Unit, onDismiss: () -> Unit) {
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(
            LocalCopy.current.common.category,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(top = Spacing.sm)) {
            all.forEach { category ->
                SkyChoiceRow(
                    label = category.name,
                    selected = category.id == selectedId,
                    onClick = { onPick(category) },
                    icon = if (icons) PtutIcon.named(category.icon) else null,
                )
            }
        }
    }
}
