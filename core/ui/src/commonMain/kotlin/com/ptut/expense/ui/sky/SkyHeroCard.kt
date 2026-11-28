package com.ptut.expense.ui.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.design.tappable

/** How far down the card the sky fades toward the page (V2 board 03: hero 62%). */
private const val HERO_FADE = 0.38f

/** The week's resting bars and the hairline over the category line, as shares of the card's ink. */
private const val BAR_REST = 0.2f
private const val LINE_ALPHA = 0.14f
private const val DAY_ALPHA = 0.75f

/**
 * #128, #131: the soft sky card that holds a screen's one big figure. It only paints the card and
 * sets its ink; each screen puts its own figure, pill, bars and line inside, in that order.
 */
@Composable
fun SkyHeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val sky = MaterialTheme.ptut.heroBg
    val fade = Brush.verticalGradient(listOf(sky, lerp(sky, MaterialTheme.colorScheme.tertiaryContainer, HERO_FADE)))
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.ptut.heroOn) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(PtutRadii.r28)
                .background(fade)
                .padding(start = Spacing.s20, end = Spacing.s20, top = Spacing.s20, bottom = Spacing.sm),
            content = content,
        )
    }
}

/** "฿220 less than a usual Sunday", on a lighter pill inside the hero card. */
@Composable
fun SkyHeroPill(text: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(top = Spacing.s12)
            .heightIn(min = SkySizes.heroPill)
            .clip(CircleShape)
            .background(MaterialTheme.ptut.heroInner)
            .padding(horizontal = Spacing.s12),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current)
    }
}

/** One day of [SkyWeekBars]: its letter, its share of the week's biggest day, and whether it is today. */
@Immutable
data class WeekBar(val letter: String, val share: Float, val today: Boolean)

/** Seven bars for the week, today's in ink, read aloud once as [spoken]. */
@Composable
fun SkyWeekBars(days: List<WeekBar>, spoken: String, modifier: Modifier = Modifier) {
    val ink = LocalContentColor.current
    val gap = Arrangement.spacedBy(Spacing.xs)
    Column(modifier.fillMaxWidth().padding(top = Spacing.s18).clearAndSetSemantics { contentDescription = spoken }) {
        Row(Modifier.fillMaxWidth().height(SkySizes.heroWeek), horizontalArrangement = gap, verticalAlignment = Alignment.Bottom) {
            days.forEach { day ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    if (day.share > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(day.share)
                                .clip(BAR_SHAPE)
                                .background(if (day.today) MaterialTheme.colorScheme.primary else ink.copy(alpha = BAR_REST)),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.s6), horizontalArrangement = gap) {
            days.forEach { day ->
                Text(
                    text = day.letter,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (day.today) FontWeight.SemiBold else null,
                    color = if (day.today) ink else ink.copy(alpha = DAY_ALPHA),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The card's last line, under a hairline: one line that ends with … rather than wrap, and opens
 * [onClick]. [spoken] replaces the drawn text for a screen reader.
 */
@Composable
fun SkyHeroLine(text: AnnotatedString, spoken: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = LocalContentColor.current
    Column(modifier.fillMaxWidth().padding(top = Spacing.xs)) {
        HorizontalDivider(thickness = SkySizes.hairline, color = ink.copy(alpha = LINE_ALPHA))
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.minTouchTarget)
                .tappable(PtutRadii.r12, Role.Button, onClick = onClick)
                .clearAndSetSemantics { contentDescription = spoken },
        ) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val BAR_SHAPE = RoundedCornerShape(
    topStart = SkySizes.chartBarCorner,
    topEnd = SkySizes.chartBarCorner,
    bottomStart = SkySizes.heroBarFoot,
    bottomEnd = SkySizes.heroBarFoot,
)
