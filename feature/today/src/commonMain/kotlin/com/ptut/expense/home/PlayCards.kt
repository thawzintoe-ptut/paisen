package com.ptut.expense.home

import com.ptut.expense.domain.Category
import com.ptut.expense.domain.MoneyParts
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.formatCompact
import com.ptut.expense.domain.formatCompactParts
import com.ptut.expense.domain.longName
import com.ptut.expense.domain.play.DayMark
import com.ptut.expense.domain.play.PERCENT
import com.ptut.expense.domain.play.TodayPlay
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.inAppLanguage
import kotlinx.datetime.LocalDate

/** One day on the run card's week: its letter, how it reads, and what a screen reader says. */
data class DayDotUiModel(val letter: String, val mark: DayMark, val label: String, val waiting: Int?)

/** #85: Main's run card. [stampId] is today's face-down stamp, which the card turns over. */
data class PlayStripUiModel(
    val eyebrow: String,
    val runTitle: String,
    val freezes: String?,
    val dots: List<DayDotUiModel>,
    val nextLine: String,
    val stampsLabel: String,
    val stampId: String?,
    val turnLabel: String,
)

/** #87: the ink Play card on Today; the whole card opens today's question. */
data class PlayQuestionCardUiModel(val eyebrow: String, val title: String, val action: String)

/** #89: the one PaiSeN line, its number, and the slice of History behind it. */
data class CoachLineUiModel(val text: String, val disc: String, val label: String, val categoryId: String, val from: String, val to: String)

/** #91: the week's challenge on Today, with its meter; the tap opens p05. */
data class ChallengeRowUiModel(val line: String, val progress: String, val fraction: Float)

/** The shelf's ways into the extras. [arg] is the month or goal the entry opens on. */
enum class ShelfEntry { Challenge, Jar, Story, Close, Score }

data class ShelfItemUiModel(val entry: ShelfEntry, val label: String, val icon: String, val arg: String? = null)

internal fun TodayPlay.challengeRow(categories: List<Category>): ChallengeRowUiModel? {
    val challenge = challenge ?: return null
    val progress = challengeProgress ?: return null
    val text = AppText.copy.play.extras
    val name = categories.firstOrNull { it.id == challenge.categoryId }?.inAppLanguage()?.name ?: return null
    return ChallengeRowUiModel(
        line = text.challengeRow(name, challenge.limit.formatCompact()),
        progress = if (progress.under) text.challengeProgress(progress.used.formatCompact(), progress.daysLeft) else text.challengeOver,
        fraction = progress.used.ratioTo(challenge.limit),
    )
}

internal fun TodayPlay.shelfItems(): List<ShelfItemUiModel> {
    val text = AppText.copy.play.extras
    return listOfNotNull(
        ShelfItemUiModel(ShelfEntry.Challenge, text.shelfChallenge, "emoji_events").takeIf { shelf.challenge && challenge == null },
        // #104: with a goal, the jar has its own card; the shelf keeps the way in to make one.
        ShelfItemUiModel(ShelfEntry.Jar, text.shelfJar, "account_balance_wallet", shelf.goalId).takeIf { shelf.jar && jar == null },
        shelf.storyMonth?.let { ShelfItemUiModel(ShelfEntry.Story, text.shelfStory, "auto_awesome", it) },
        shelf.closeMonth?.let { ShelfItemUiModel(ShelfEntry.Close, text.shelfClose, "task_alt", it) },
        ShelfItemUiModel(ShelfEntry.Score, text.shelfScore, "insights").takeIf { shelf.score },
    )
}

/**
 * J01: the jar on Today. [plan] says the weekly pace and the month it lands; [nudge] and [putIn] show on a
 * day under its usual, so the leftover is one button away. The jar fills only from money that moved.
 */
data class JarCardUiModel(
    val open: ShelfItemUiModel,
    val goalId: String,
    val name: String,
    val saved: MoneyParts,
    val ofTarget: String,
    val fraction: Float,
    val label: String,
    val plan: String?,
    val thisWeek: String?,
    val nudge: String?,
    /** The amount inside [nudge], set bold (J01). */
    val nudgeAmount: String? = null,
    val putIn: String?,
)

internal fun TodayPlay.jarCard(): JarCardUiModel? {
    val jar = jar ?: return null
    val text = AppText.copy.play.extras
    val fraction = jar.pace.saved.ratioTo(jar.goal.target).coerceIn(0f, 1f)
    val percent = (fraction * PERCENT).toInt()
    val perWeek = jar.pace.perWeek
    val reach = jar.pace.reachOn
    return JarCardUiModel(
        open = ShelfItemUiModel(ShelfEntry.Jar, text.shelfJar, "account_balance_wallet", jar.goal.id),
        goalId = jar.goal.id,
        name = reach?.let { text.jarGoalBy(jar.goal.name, it.month.longName()) } ?: jar.goal.name,
        saved = jar.pace.saved.formatCompactParts(),
        ofTarget = text.jarOf(jar.goal.target.formatCompact()),
        fraction = fraction,
        label = text.jarLabel(percent),
        plan = when {
            jar.pace.fills(jar.goal.target) -> text.jarFull
            perWeek == null -> null
            reach == null -> text.jarPlanFar(perWeek.formatCompact())
            else -> text.jarPlan(perWeek.formatCompact(), reach.month.longName())
        },
        // J05: once money went in this week; J01 has no such line.
        thisWeek = perWeek?.takeIf { jar.thisWeek > Satang.Zero }?.let { text.jarThisWeek(jar.thisWeek.formatCompact(), it.formatCompact(), jar.thisWeek < it) },
        nudge = jar.under?.let { text.jarNudge(it.formatCompact(), jar.weekday.longName()) },
        nudgeAmount = jar.under?.formatCompact(),
        putIn = jar.under?.let { text.jarPutInAmount(it.formatCompact()) },
    )
}

internal fun TodayPlay.questionCard(): PlayQuestionCardUiModel? = AppText.copy.play.question.takeIf { question }?.let { text ->
    PlayQuestionCardUiModel(eyebrow = text.cardEyebrow, title = text.cardTitle, action = text.cardAction)
}

/** D9: shown in Compared's place; the tap opens today's rows in that category. */
internal fun TodayPlay.coachCard(today: LocalDate, categories: List<Category>): CoachLineUiModel? {
    val line = coach ?: return null
    val text = AppText.copy.play.coach
    val name = categories.firstOrNull { it.id == line.categoryId }?.inAppLanguage()?.name ?: return null
    val amount = line.difference.abs().formatCompact()
    val weekday = line.weekday.longName()
    val said = if (line.difference > Satang.Zero) text.more(name, amount, weekday) else text.less(name, amount, weekday)
    return CoachLineUiModel(said, text.disc, text.label(said), line.categoryId, today.toString(), today.toString())
}

/** Null with the run switched off, so Today draws no card at all. */
internal fun TodayPlay.strip(queued: Int): PlayStripUiModel? {
    val run = run ?: return null
    val text = AppText.copy.play.run
    val weekdays = AppText.copy.calendar.weekdays
    val dots = run.week.mapIndexed { index, mark ->
        val day = weekdays[index]
        DayDotUiModel(
            letter = text.weekLetters[index],
            mark = mark,
            label = when (mark) {
                DayMark.Cleared -> text.dayCleared(day)
                DayMark.Frozen -> text.dayFrozen(day)
                DayMark.Missed -> text.dayMissed(day)
                DayMark.Today -> text.dayToday(day, queued)
                DayMark.Ahead -> text.dayAhead(day)
            },
            waiting = queued.takeIf { mark == DayMark.Today && it > 0 },
        )
    }
    val next = when {
        run.todayCleared && stampWaiting != null -> text.clearToday
        run.todayCleared -> text.keptToday
        queued > 0 -> text.next(queued, run.length + 1)
        else -> text.startLine
    }
    return PlayStripUiModel(
        eyebrow = text.eyebrow,
        runTitle = text.days(run.length),
        freezes = run.freezes.takeIf { it > 0 }?.let(text.freezes),
        dots = dots,
        nextLine = next,
        stampsLabel = text.stamps,
        stampId = stampWaiting,
        turnLabel = text.turnStamp,
    )
}
