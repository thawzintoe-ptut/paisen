package com.ptut.expense.domain.play

import com.ptut.expense.domain.settings.PlayChoices

/**
 * One play feature (#82). [released] false keeps it to debug builds until it has been checked on a
 * phone (D17); a part flips to released in its own change, after that check.
 */
enum class PlayPart(private val released: Boolean) {
    Run(true),
    Stamps(true),
    Question(true),
    Guess(true),
    Coach(true),
    Story(true),
    Challenge(true),
    GoalJar(true),
    CloseMonth(true),
    Score(true),
    SettlePlan(true),
    Wheel(true),
    AllSquare(true),
    Reactions(true),
    Awards(true),
    TripRecap(true),
    Reminder(true),
    ;

    fun offered(debug: Boolean): Boolean = debug || released
}

/**
 * Whether the reader's switches let [part] show. The master turns everything off but the jar, which
 * is v1 and has no play switch in front of it (#116); a part with no sub-switch of its own on p22
 * follows the master alone (D14).
 */
fun PlayChoices.allows(part: PlayPart): Boolean = when (part) {
    PlayPart.GoalJar -> true

    // The day's question fills a freeze, so it belongs to the run.
    PlayPart.Run, PlayPart.Stamps, PlayPart.Question -> on && run

    PlayPart.Guess -> on && guess

    PlayPart.Challenge -> on && challenges

    PlayPart.AllSquare, PlayPart.Awards -> on && groupStamps

    PlayPart.Reminder -> on && weeklyReminder

    PlayPart.Coach, PlayPart.Story, PlayPart.CloseMonth, PlayPart.Score,
    PlayPart.SettlePlan, PlayPart.Wheel, PlayPart.Reactions, PlayPart.TripRecap,
    -> on
}

/**
 * What this build offers before the reader's switches: the Remote Config flag and each part's
 * release (D8, D17). Read once at launch, so it never changes under an open screen.
 */
data class PlayGate(
    val flagOn: Boolean,
    val debug: Boolean,
    val tipsOn: Boolean = true,
    /** #116: the two release switches. The jar is v1 and waits on neither. */
    val v2On: Boolean = true,
    val laterOn: Boolean = true,
) {

    fun offers(part: PlayPart): Boolean = flagOn && part.offered(debug) && when (part) {
        PlayPart.GoalJar -> true

        // M03: thanks for a payment is a Should, not play.
        PlayPart.Reactions -> v2On

        else -> laterOn
    }

    companion object {
        /** A graph that binds no gate, such as a screen test's, offers no play at all. */
        val Off = PlayGate(flagOn = false, debug = false, v2On = false, laterOn = false)
    }
}
