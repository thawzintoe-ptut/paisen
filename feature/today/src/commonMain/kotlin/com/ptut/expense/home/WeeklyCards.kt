package com.ptut.expense.home

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.today.TargetWeek
import com.ptut.expense.domain.today.WeeklyLookBack
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.NoteTone
import com.ptut.expense.locale.noteCopy
import com.ptut.expense.locale.targetCopy
import com.ptut.expense.ui.inAppLanguage

/** #20: "Your week", its sentences, its trend glyph, and the slice of History a tap opens: ISO days, Sunday included. */
data class WeeklyCardUiModel(
    val lead: String,
    val line: String,
    val glyph: String,
    val from: String,
    val to: String,
    val categoryId: String?,
    /** #22: the line carries a note, so "That's not right" sits under it. */
    val showsNotRight: Boolean = false,
    /** #86: last week's Monday while its guess waits; the card opens the guess first (D4). */
    val guessMonday: String? = null,
)

/** #21: "Food: ฿1,900 of ฿2,500 this week" and how far along the bar is, full at the target. */
data class TargetUiModel(val line: String, val fraction: Float)

/** #22: whether the weekly note is on in this build, its tone, and the Monday whose note was hidden. */
data class WeeklyNoteSettings(val on: Boolean = false, val tone: NoteTone = NoteTone.Plain, val hiddenWeek: String? = null)

/** #20: last week beside a usual one, then the category that rose most. Never red, never "over". */
internal fun WeeklyLookBack.asCard(categories: List<Category>, tone: NoteTone): WeeklyCardUiModel {
    if (noted) return noteCard(categories, tone)
    val home = AppText.copy.home
    val difference = check.difference
    val versus = when {
        difference == null -> ""
        difference > Satang.Zero -> home.weekMore(difference.formatCompact())
        difference < Satang.Zero -> home.weekLess((-difference).formatCompact())
        else -> home.weekSame
    }
    val rise = check.rise
    val riseName = rise?.let { categories.shownName(it.categoryId) }
    val next = if (rise != null && riseName != null) " " + home.weekRise(riseName, rise.difference.formatCompact(), rise.shops) else ""
    return WeeklyCardUiModel(
        lead = home.weekLead,
        line = home.weekCameTo(check.spent.formatCompact(), versus) + next + targetSentence(target, categories),
        glyph = difference.trendGlyph(),
        from = check.from.toString(),
        to = check.to.toString(),
        categoryId = rise?.categoryId.takeIf { riseName != null },
    )
}

/** #21: null when the target's category is gone, so a deleted category draws nothing. */
internal fun TargetWeek.targetLine(categories: List<Category>): TargetUiModel? {
    val name = categories.shownName(categoryId) ?: return null
    return TargetUiModel(
        line = targetCopy().weekLine(name, progress.spent.formatCompact(), progress.target.formatCompact()),
        fraction = progress.fraction,
    )
}

/** #21: Monday's word on last week's target, plain either way. */
private fun targetSentence(target: TargetWeek?, categories: List<Category>): String {
    if (target == null) return ""
    val name = categories.shownName(target.categoryId) ?: return ""
    val progress = target.progress
    val words = targetCopy()
    val amount = progress.target.formatCompact()
    return " " + if (progress.over > Satang.Zero) {
        words.wentPast(name, progress.over.formatCompact(), amount)
    } else {
        words.stayedUnder(name, amount)
    }
}

/**
 * #22: the #20 card in the chosen tone. Planned spending is already out of the rise; a rise with
 * planned money beside it says how much was planned. A week under or on usual says so in the tone.
 */
private fun WeeklyLookBack.noteCard(categories: List<Category>, tone: NoteTone): WeeklyCardUiModel {
    val home = AppText.copy.home
    val words = noteCopy(tone)
    val difference = check.difference
    val rise = check.rise
    val riseName = rise?.let { categories.shownName(it.categoryId) }
    val versus = difference?.takeIf { it > Satang.Zero }?.let { home.weekMore(it.formatCompact()) }.orEmpty()
    val line = if (rise != null && riseName != null) {
        val said = if (rise.planned > Satang.Zero) {
            words.roseAfterPlanned(riseName, rise.difference.formatCompact(), rise.planned.formatCompact())
        } else {
            words.rose(riseName, rise.difference.formatCompact(), rise.shops.firstOrNull())
        }
        home.weekCameTo(check.spent.formatCompact(), versus) + " " + said
    } else {
        val said = when {
            difference == null -> null
            difference < Satang.Zero -> words.under((-difference).formatCompact())
            difference == Satang.Zero -> words.same
            else -> null
        }
        home.weekCameTo(check.spent.formatCompact(), versus) + (said?.let { " $it" } ?: "")
    }
    return WeeklyCardUiModel(
        lead = home.weekLead,
        line = line + targetSentence(target, categories),
        glyph = difference.trendGlyph(),
        from = check.from.toString(),
        to = check.to.toString(),
        categoryId = rise?.categoryId.takeIf { riseName != null },
        showsNotRight = true,
    )
}

/** Up, down, or flat for no change and for nothing to compare against. */
internal fun Satang?.trendGlyph(): String = when {
    this == null || this == Satang.Zero -> "trending_flat"
    this > Satang.Zero -> "trending_up"
    else -> "trending_down"
}

/** The category's name in the app's language, or null once it has been deleted. */
private fun List<Category>.shownName(id: String): String? = firstOrNull { it.id == id }?.inAppLanguage()?.name
