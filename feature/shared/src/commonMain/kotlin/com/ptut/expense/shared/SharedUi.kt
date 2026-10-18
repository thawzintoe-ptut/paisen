package com.ptut.expense.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptut
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.formatParts
import com.ptut.expense.domain.shared.GroupType
import com.ptut.expense.domain.shared.Member
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.spoken
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyEmptyState
import com.ptut.expense.ui.sky.SkyRollingAmount

/**
 * The parts every v2 screen shares (#136–#141). Direction is always in words as well as colour
 * (p26): owed is green, owe is ink (decided 13 Oct, red stays for delete and stale data), settled
 * is grey.
 */
enum class BalanceTone { Owed, Owe, Settled }

fun Satang.tone(): BalanceTone = when {
    this > Satang.Zero -> BalanceTone.Owed
    this < Satang.Zero -> BalanceTone.Owe
    else -> BalanceTone.Settled
}

fun Satang.magnitude(): Satang = if (this < Satang.Zero) -this else this

@Composable
fun BalanceTone.ink(): Color = when (this) {
    BalanceTone.Owed -> MaterialTheme.ptut.ok
    BalanceTone.Owe -> MaterialTheme.ptut.signatureInk
    BalanceTone.Settled -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** p34's chips fill in a name and an icon (#141). */
fun GroupType.defaultIcon(): String = when (this) {
    GroupType.Trip -> PtutIcon.Work.designName
    GroupType.Flat -> PtutIcon.Home.designName
    GroupType.Couple, GroupType.Other -> PtutIcon.Group.designName
}

fun GroupType.label(): String {
    val text = AppText.copy.shared
    return when (this) {
        GroupType.Trip -> text.typeTrip
        GroupType.Flat -> text.typeFlat
        GroupType.Couple -> text.typeCouple
        GroupType.Other -> text.group
    }
}

/** This phone's member is "You" everywhere, whatever name it is stored under (#187 gives it one). */
fun SharedGroup.displayName(memberId: String): String =
    if (isMe(memberId)) AppText.copy.common.you else member(memberId)?.name ?: AppText.copy.shared.someone

fun Member.initial(): String = name.trim().firstOrNull()?.uppercase() ?: "?"

/** A member as p26–p31 draw them: an initial on pale sky, or "You" on ink. */
@Composable
fun MemberAvatar(label: String, isMe: Boolean, modifier: Modifier = Modifier, size: Dp = SkySizes.rowCircle) {
    val you = LocalCopy.current.common.you
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.ptut.infoBg),
    ) {
        Text(
            text = if (isMe) you else label,
            style = MaterialTheme.typography.titleSmall,
            color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** A group's icon on a circle: sky for a trip, ink for a home (p26). */
@Composable
fun GroupTile(icon: String, ink: Boolean, modifier: Modifier = Modifier, size: Dp = SkySizes.rowCircle) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (ink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
    ) {
        Icon(
            imageVector = PtutIcon.named(icon).image,
            contentDescription = null,
            tint = if (ink) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(SkySizes.rowGlyph),
        )
    }
}

/** p26 and p27's big figure: the baht sign and the satang smaller, in the balance's colour. */
@Composable
fun SharedHero(label: String, amount: Satang, tint: Color, modifier: Modifier = Modifier) {
    val parts = amount.formatParts()
    Column(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label, ${amount.spoken()}" }) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // #200: a new balance rolls to its figure, so the change is seen as well as read.
        SkyRollingAmount(
            symbol = "${parts.symbol} ",
            whole = parts.whole,
            fraction = parts.fraction,
            color = tint,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/** A screen whose group left this phone while it was open (deleted for everyone, or you were taken out). */
@Composable
internal fun GroupGone(status: String, modifier: Modifier = Modifier) {
    val text = LocalCopy.current.shared
    SkyEmptyState(
        headline = text.thisGroupIsGone,
        body = text.detail.goneBody,
        status = status,
        statusIcon = PtutIcon.Check.image,
        modifier = modifier.padding(horizontal = Spacing.md).padding(top = Spacing.md),
    )
}
