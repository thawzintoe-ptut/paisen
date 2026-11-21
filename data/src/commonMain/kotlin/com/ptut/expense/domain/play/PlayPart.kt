package com.ptut.expense.domain.play

import com.ptut.expense.domain.settings.PlayChoices

/**
 * One play feature (#82). [released] false keeps it to debug builds until it has been checked on a
 * phone (D17); a part flips to released in its own change, after that check.
 */
enum class PlayPart(private val released: Boolean) {
    Run(false),
    Stamps(false),
    Question(false),
    Guess(false),
    Coach(false),
    Story(false),
    Challenge(false),
    GoalJar(false),
    CloseMonth(false),
    Score(false),
    SettlePlan(false),
    Wheel(false),
    AllSquare(false),
    Reactions(false),
    Awards(false),
    TripRecap(false),
    Reminder(false),
    ;

    fun offered(debug: Boolean): Boolean = debug || released
}

/**
 * Whether the reader's switches let [part] show. The master turns everything off; a part with no
 * sub-switch of its own on p22 follows the master alone (D14).
 */
fun PlayChoices.allows(part: PlayPart): Boolean = on && when (part) {
    // The day's question fills a freeze, so it belongs to the run.
    PlayPart.Run, PlayPart.Stamps, PlayPart.Question -> run

    PlayPart.Guess -> guess

    PlayPart.Challenge -> challenges

    PlayPart.AllSquare, PlayPart.Awards -> groupStamps

    PlayPart.Reminder -> weeklyReminder

    PlayPart.Coach, PlayPart.Story, PlayPart.GoalJar, PlayPart.CloseMonth, PlayPart.Score,
    PlayPart.SettlePlan, PlayPart.Wheel, PlayPart.Reactions, PlayPart.TripRecap,
    -> true
}

/**
 * What this build offers before the reader's switches: the Remote Config flag and each part's
 * release (D8, D17). Read once at launch, so it never changes under an open screen.
 */
data class PlayGate(val flagOn: Boolean, val debug: Boolean) {

    fun offers(part: PlayPart): Boolean = flagOn && part.offered(debug)

    companion object {
        /** A graph that binds no gate, such as a screen test's, offers no play at all. */
        val Off = PlayGate(flagOn = false, debug = false)
    }
}
