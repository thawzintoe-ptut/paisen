package com.ptut.expense.shoprule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.ptut.expense.design.Spacing
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.shared.SharedTextField
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyChip
import com.ptut.expense.ui.sky.SkyIntro
import com.ptut.expense.ui.sky.SkyListScreen

/**
 * FR-12's correction, on a screen of its own.
 *
 * Its own screen and not a control on the record, because the record is a place to look: FR-21
 * gives it a whole screen so the reader reads it before the one action it leads to. This is the
 * separate, deliberate act screen 11c says changing a shop's filing has to be.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShopRuleView(
    uiState: ShopRuleUiState,
    onBack: () -> Unit,
    onCategory: (String) -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
    onRename: (String) -> Unit = {},
) {
    val text = LocalCopy.current.slips
    SkyListScreen(title = text.shopRuleTitle, onBack = onBack, modifier = modifier) {
        when (uiState) {
            ShopRuleUiState.Loading -> Unit

            ShopRuleUiState.Missing -> item {
                SkyIntro(text.shopNotRemembered)
            }

            is ShopRuleUiState.Loaded -> {
                item {
                    Text(
                        text = uiState.shop,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
                }
                if (uiState.nameable) {
                    item { ShopNameField(name = uiState.name, onRename = onRename) }
                }
                item { SkyIntro(uiState.filedSoFar) }
                item { SkyIntro(text.shopFilesHere) }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.padding(horizontal = Spacing.sm),
                    ) {
                        uiState.categories.forEach { category ->
                            SkyChip(
                                label = category.name,
                                selected = category.id == uiState.chosenCategoryId,
                                onClick = { onCategory(category.id) },
                            )
                        }
                    }
                }
                item {
                    // Outlined in red and never filled, as the record's delete is.
                    SkyButton(
                        label = text.forgetShop,
                        onClick = onForget,
                        kind = SkyButtonKind.DangerOutline,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm).padding(top = Spacing.xs),
                    )
                }
                item { SkyIntro(text.forgetNote) }
            }
        }
    }
}

/** Research finding 2: the name a Thai shop never reads as. Typed here, saved as it is typed. */
@Composable
private fun ShopNameField(name: String, onRename: (String) -> Unit) {
    val text = LocalCopy.current.slips
    var typed by rememberSaveable { mutableStateOf(name) }
    Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
        Text(
            text.shopReadAsNoise,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        SharedTextField(
            value = typed,
            onValueChange = {
                typed = it
                onRename(it)
            },
            label = text.shopName,
            placeholder = text.shopNameExample,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}
