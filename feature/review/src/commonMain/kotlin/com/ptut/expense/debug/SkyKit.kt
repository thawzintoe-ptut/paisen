package com.ptut.expense.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.QuietDayArt
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.SlipDropArt
import com.ptut.expense.design.SlipFlightArt
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutType
import com.ptut.expense.domain.Direction
import com.ptut.expense.ui.sky.SkyBarIconAction
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyCheckbox
import com.ptut.expense.ui.sky.SkyChip
import com.ptut.expense.ui.sky.SkyConfidenceBadge
import com.ptut.expense.ui.sky.SkyConfirmAction
import com.ptut.expense.ui.sky.SkyDashedAddRow
import com.ptut.expense.ui.sky.SkyEmptyState
import com.ptut.expense.ui.sky.SkyGroupedCard
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkyKeyValueRow
import com.ptut.expense.ui.sky.SkyLargeTitle
import com.ptut.expense.ui.sky.SkyNoticeCard
import com.ptut.expense.ui.sky.SkyPageSheet
import com.ptut.expense.ui.sky.SkySearchField
import com.ptut.expense.ui.sky.SkySectionHeader
import com.ptut.expense.ui.sky.SkySegmentedToggle
import com.ptut.expense.ui.sky.SkySheetSurface
import com.ptut.expense.ui.sky.SkyStatCard
import com.ptut.expense.ui.sky.SkyStatusPill
import com.ptut.expense.ui.sky.SkyStatusPillTone
import com.ptut.expense.ui.sky.SkySubChipBox
import com.ptut.expense.ui.sky.SkySwitch
import com.ptut.expense.ui.sky.SkyTextAction
import com.ptut.expense.ui.sky.SkyTopBar
import com.ptut.expense.ui.sky.SkyTransactionRow
import com.ptut.expense.ui.sky.SkyUnderlineTabs
import com.ptut.expense.ui.sky.SkyWarningBanner

/**
 * Every Sky control and surface in one column (#151–#154), kept for `TouchTargetFloorTest` to
 * sweep. No screen shows it; the debug menu's panel for it was taken out.
 */
@Composable
fun SkyKit(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        SkyBars()
        SkyButton(label = "Save", onClick = {}, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s10)) {
            SkyButton("Not mine", onClick = {}, modifier = Modifier.weight(1f), kind = SkyButtonKind.Outline)
            SkyButton("Confirm", onClick = {}, modifier = Modifier.weight(1.6f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s10)) {
            SkyButton("Keep it", onClick = {}, modifier = Modifier.weight(1f), kind = SkyButtonKind.Outline)
            SkyButton("Delete", onClick = {}, modifier = Modifier.weight(1f), kind = SkyButtonKind.Danger)
        }
        SkyButton("Delete", onClick = {}, modifier = Modifier.fillMaxWidth(), kind = SkyButtonKind.DangerOutline)
        SkyButton("Save · 2 folders", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkyTextAction(label = "Save", onClick = {}, enabled = true)
            SkyTextAction(label = "Save", onClick = {}, enabled = false)
        }
        SkyChoices()
        SkyInputs()
        SkyRows()
        SkyCards()
        SkySheetAndPills()
        SkyEmptyState(
            headline = "A quiet day so far.",
            body = "New slips in your chosen folders show up here on their own. Or add one by hand.",
            status = "Watching the K PLUS folder · checked 4 min ago",
            statusIcon = PtutIcon.Sync.image,
            illustration = { QuietDayArt() },
            waiting = true,
            cta = "Add by hand",
            onCta = {},
        )
        SkyDrawings()
    }
}

/** #180's other two drawings, in the slots the delete sheet and first run give them. */
@Composable
private fun SkyDrawings() {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(SkySizes.deleteIllustrationRatio)
            .clip(PtutRadii.r22)
            .background(MaterialTheme.ptut.infoBg),
    ) { SlipDropArt() }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(SkySizes.firstRunIllustrationRatio)
            .clip(PtutRadii.r28)
            .background(MaterialTheme.ptut.barTrack),
    ) { SlipFlightArt() }
}

@Composable
private fun SkyBars() {
    SkyLargeTitle(text = "Settings")
    SkyTopBar(
        title = "Purchase",
        onBack = {},
        trailing = { SkyBarIconAction(icon = PtutIcon.Edit, label = "Edit", onClick = {}) },
    )
    SkyTopBar(
        title = "Edit purchase",
        onBack = {},
        backIcon = PtutIcon.Close,
        backLabel = "Close",
        trailing = { SkyConfirmAction(enabled = true, onClick = {}) },
    )
    SkyTopBar(
        title = "Categories",
        onBack = {},
        trailing = { SkyBarIconAction(icon = PtutIcon.Add, label = "New category", onClick = {}) },
    )
}

@Composable
private fun SkySheetAndPills() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkyStatusPill(label = "Not counted")
        SkyStatusPill(label = "Amount unclear", tone = SkyStatusPillTone.Loud)
    }
    SkyConfidenceBadge(label = "Amount read clearly")
    // The surface only: a modal sheet would cover the screen the kit sits on.
    Box(
        modifier = Modifier
            .clip(PtutRadii.r16)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.xs),
    ) {
        SkySheetSurface {
            Text(
                text = "Delete this purchase?",
                style = MaterialTheme.ptutType.sectionTitle,
                modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s10)) {
                SkyButton("Keep it", onClick = {}, modifier = Modifier.weight(1f), kind = SkyButtonKind.Outline)
                SkyButton("Delete", onClick = {}, modifier = Modifier.weight(1f), kind = SkyButtonKind.Danger)
            }
        }
    }
}

@Composable
private fun SkyCards() {
    SkyWarningBanner(
        title = "No slips seen for 3 days",
        body = "Totals below may be incomplete",
        actionLabel = "Fix",
        onAction = {},
    )
    SkyWarningBanner(title = "No slips seen for 2 days", body = "Totals below may be incomplete")
    SkyNoticeCard(
        title = "Why Groceries?",
        text = "You picked it for Foodland on 2 Aug. 11 slips since went the same way.",
        icon = PtutIcon.AutoAwesome.image,
    )
    // On the grey page, as Home draws them: white on white would hide the cards' edges.
    Column(
        modifier = Modifier
            .clip(PtutRadii.r16)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.s12),
        verticalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            SkyStatCard(
                title = "Compared",
                line = "฿180 less than a usual Friday",
                icon = PtutIcon.TrendingDown.image,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            SkyStatCard(
                title = "To check",
                line = "2 slips to check",
                icon = PtutIcon.Inbox.image,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = {},
            )
        }
        SkyGroupedCard(
            rows = listOf(
                { SkyKeyValueRow(label = "Photo folders", value = "Screenshots, K PLUS", modifier = Modifier.padding(horizontal = Spacing.sm)) },
                { SkyKeyValueRow(label = "Timezone", value = "Asia/Bangkok", modifier = Modifier.padding(horizontal = Spacing.sm)) },
            ),
        )
        SkyPageSheet {
            SkySectionHeader(text = "Purchases", onClick = {})
            SkySectionHeader(text = "Top categories")
        }
    }
}

@Composable
private fun SkyRows() {
    Column {
        SkyHairline()
        SkyKeyValueRow(label = "Category", value = "Groceries · Supermarket")
        SkyKeyValueRow(label = "From", value = "K PLUS slip · Screenshots")
    }
    Column {
        SkyTransactionRow(
            merchant = "Foodland Rama IV",
            meta = "Groceries",
            amount = "−฿642.50",
            icon = "local_grocery_store",
            direction = Direction.Expense,
            time = "18:30",
            onClick = {},
        )
        SkyTransactionRow(
            merchant = "Salary",
            meta = "Income · added by hand",
            amount = "+฿32,000.00",
            icon = "account_balance_wallet",
            direction = Direction.Income,
        )
    }
}

@Composable
private fun SkyInputs() {
    var background by remember { mutableStateOf(true) }
    var transparency by remember { mutableStateOf(false) }
    var screenshots by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkySwitch(checked = background, onCheckedChange = { background = it }, label = "Background import")
        SkySwitch(checked = transparency, onCheckedChange = { transparency = it }, label = "Transparency")
        SkyCheckbox(checked = screenshots, onCheckedChange = { screenshots = it }, label = "Screenshots")
        SkyCheckbox(checked = camera, onCheckedChange = { camera = it }, label = "Camera")
    }
    SkySearchField(query = query, onSearch = { query = it }, placeholder = "Search purchases")
    SkyDashedAddRow(label = "New category", onClick = {})
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkyChoices() {
    var period by remember { mutableIntStateOf(0) }
    var kind by remember { mutableIntStateOf(0) }
    var category by remember { mutableIntStateOf(0) }
    var sub by remember { mutableIntStateOf(0) }
    val categories = listOf("Groceries", "Transport", "Food & drink")
    val subs = listOf("Supermarket", "Market", "Household")

    SkyUnderlineTabs(options = listOf("Today", "Week", "Month"), selected = period, onSelect = { period = it })
    SkySegmentedToggle(options = listOf("Expense", "Income"), selected = kind, onSelect = { kind = it })
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        categories.forEachIndexed { index, name ->
            // The first at the add sheet's height, so both chip heights are on show.
            SkyChip(
                label = name,
                selected = index == category,
                onClick = { category = index },
                height = if (index == 0) SkySizes.sheetChip else SkySizes.chip,
            )
        }
    }
    SkySubChipBox(
        options = subs,
        name = { it },
        selected = subs.getOrNull(sub),
        onSelect = { sub = subs.indexOf(it) },
    )
}
