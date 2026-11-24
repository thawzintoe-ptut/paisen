package com.ptut.expense.domain.play

import com.ptut.expense.domain.settings.PlayChoices

/**
 * A just-in-time tip: shown beside [part] the first time it appears, once. [after] holds a tip back
 * until another on the same screen has been seen, so a screen shows one tip at a time.
 */
enum class PlayTip(val part: PlayPart, val number: Int, val after: PlayTip? = null) {
    Run(PlayPart.Run, number = 1),
    Question(PlayPart.Question, number = 2, after = Run),
    Stamp(PlayPart.Stamps, number = 3),
    Guess(PlayPart.Guess, number = 4),
    Jar(PlayPart.GoalJar, number = 5),
    Wheel(PlayPart.Wheel, number = 6),
    ;

    companion object {
        val count: Int = entries.size
    }
}

/** Whether [tip] shows now: its part shows, tips are on, it has not been seen, and the tip before it has. */
fun PlayChoices.shows(tip: PlayTip, gate: PlayGate): Boolean =
    on && gate.tipsOn && gate.offers(tip.part) && allows(tip.part) && tips && tip.name !in seenTips && (tip.after == null || tip.after.name in seenTips)
