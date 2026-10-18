package com.ptut.expense.ui.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.ptutType
import com.ptut.expense.design.tappable

/** Settings' white card on the grey page (p21), a hairline between its rows, 16 in from each side. */
@Composable
fun SkyGroupedCard(rows: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PtutRadii.r20)
            .background(MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) SkyHairline(Modifier.padding(horizontal = Spacing.sm))
            row()
        }
    }
}

/** The white sheet the day's purchases and the top categories sit on (p05, p14): grey page, round top. */
@Composable
fun SkyPageSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = SkySizes.sheetCorner, topEnd = SkySizes.sheetCorner))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(start = Spacing.s20, end = Spacing.s20, top = Spacing.s20),
        content = content,
    )
}

/** "Purchases >" (p05). The chevron and the tap come together: a title that goes nowhere draws none. */
@Composable
fun SkySectionHeader(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .heightIn(min = Sizes.minTouchTarget)
            .then(if (onClick == null) Modifier else Modifier.tappable(PtutRadii.r12, Role.Button, onClick = onClick)),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.ptutType.sectionTitle, color = MaterialTheme.colorScheme.onSurface)
        if (onClick != null) {
            Icon(
                imageVector = PtutIcon.ChevronRight.image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(SkySizes.cardGlyph),
            )
        }
    }
}

/**
 * Something to know before acting (p07, p16), in pale sky because Sky's red means delete and stale
 * data only. A [title] leads [text] in a heavier weight, as "Why Groceries?" does.
 */
@Composable
fun SkyNoticeCard(text: String, icon: ImageVector, modifier: Modifier = Modifier, title: String? = null) {
    val ink = MaterialTheme.ptut.infoInk
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PtutRadii.r16)
            .background(MaterialTheme.ptut.infoBg)
            .padding(horizontal = Spacing.sm, vertical = Spacing.s14),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = ink, modifier = Modifier.size(SkySizes.cardGlyph))
        Text(
            text = buildAnnotatedString {
                if (title != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(title) }
                    append(" ")
                }
                append(text)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
        )
    }
}

/**
 * The stale-import banner (p06): red tint, a warning glyph, two lines and a red Fix pill. Without an
 * action it is the quiet state C-5 asks for, one to three days stale with nothing to tap.
 */
@Composable
fun SkyWarningBanner(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PtutRadii.r16)
            .background(MaterialTheme.ptut.warnBg)
            .padding(start = Spacing.sm, end = Spacing.s12, top = Spacing.xs, bottom = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = PtutIcon.Error.image,
            contentDescription = null,
            tint = MaterialTheme.ptut.warnInk,
            modifier = Modifier.size(SkySizes.cardGlyph),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = Spacing.xxs),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            body?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (actionLabel != null && onAction != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = Sizes.minTouchTarget)
                    .tappable(CircleShape, Role.Button, onClick = onAction),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = SkySizes.bannerAction)
                        .clip(CircleShape)
                        .background(MaterialTheme.ptut.warn)
                        .padding(horizontal = Spacing.s15),
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.ptut.warnOn)
                }
            }
        }
    }
}

/**
 * "Compared" and "To check" on Home (p05): a white card on the grey page, two lines and a glyph.
 * The glyph sits at the bottom, so two cards given one height line their glyphs up as p05 does.
 */
@Composable
fun SkyStatCard(
    title: String,
    line: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** Ruby with white on it, for a card that is waiting on the reader. */
    filled: Boolean = false,
) {
    val ink = if (filled) MaterialTheme.ptut.onSignature else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(PtutRadii.r20)
            .then(
                if (onClick == null) {
                    Modifier.semantics(mergeDescendants = true) {}
                } else {
                    Modifier.tappable(PtutRadii.r20, Role.Button, onClick = onClick)
                },
            )
            .background(if (filled) MaterialTheme.ptut.signature else MaterialTheme.colorScheme.tertiaryContainer)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ink)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = if (filled) ink else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.padding(top = Spacing.s10).size(SkySizes.cardGlyph),
        )
    }
}

/**
 * p07 and p08's card under the tabs: an ink circle with a glyph, a grey lead, one sentence and a
 * chevron, on pale sky. The whole card opens the day's receipt (#121, #122).
 */
@Composable
fun SkyMoodCard(lead: String, line: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PtutRadii.r20)
            .tappable(PtutRadii.r20, Role.Button, onClick = onClick)
            .background(MaterialTheme.ptut.infoBg)
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(SkySizes.rowCircle).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(SkySizes.rowGlyph))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(lead, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Icon(PtutIcon.ChevronRight.image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(Sizes.settingsTrailing))
    }
}
