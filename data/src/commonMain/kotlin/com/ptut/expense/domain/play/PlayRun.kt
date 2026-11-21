package com.ptut.expense.domain.play

import com.ptut.expense.domain.DAYS_IN_WEEK
import com.ptut.expense.domain.mondayOfWeek
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Three answered questions fill one freeze (D3). */
const val ANSWERS_PER_FREEZE = 3

/** A reader holds at most two freezes (D3). */
const val MAX_FREEZES = 2

/** How a day on the run card's week reads. A missed day is a blank, never red (R7). */
enum class DayMark { Cleared, Frozen, Missed, Today, Ahead }

/** #85: the tray-clear run as of today, worked out from the days the reader cleared and answered. */
data class PlayRun(
    val length: Int,
    val freezes: Int,
    /** Answers toward the next freeze, 0 to 2. */
    val freezeProgress: Int,
    /** Monday to Sunday of today's week. */
    val week: List<DayMark>,
    val todayCleared: Boolean,
) {
    companion object {
        val Empty = PlayRun(length = 0, freezes = 0, freezeProgress = 0, week = List(DAYS_IN_WEEK) { DayMark.Ahead }, todayCleared = false)
    }
}

/**
 * The run from the first day of play to [today] (D2, D3). A cleared day adds one. A missed day spends
 * a freeze while the run has one to keep, and ends the run quietly when it has none. Answers on a
 * day count before the day is judged, so the day's question can save that same day. Today is not
 * judged until it ends: an uncleared today keeps yesterday's run.
 *
 * Days are the home zone's dates as written; a later zone change only moves where today falls.
 */
fun List<PlayDay>.playRun(today: LocalDate): PlayRun {
    if (isEmpty()) return PlayRun.Empty.copy(week = weekMarks(today, emptyMap()))
    val byDate = associateBy { it.date }
    val first = minOf { it.date }
    var length = 0
    var freezes = 0
    var progress = 0
    val marks = HashMap<LocalDate, DayMark>()
    var day = first
    val last = maxOf(today, maxOf { it.date })
    while (day <= last) {
        val row = byDate[day]
        if (row?.answeredAtEpochMillis != null) {
            progress++
            if (progress == ANSWERS_PER_FREEZE) {
                // With both freezes held, the count waits one short until a freeze is spent, so no fill is lost.
                if (freezes < MAX_FREEZES) {
                    progress = 0
                    freezes++
                } else {
                    progress = ANSWERS_PER_FREEZE - 1
                }
            }
        }
        marks[day] = when {
            row?.clearedAtEpochMillis != null -> DayMark.Cleared.also { length++ }
            day >= today -> DayMark.Today
            length > 0 && freezes > 0 -> DayMark.Frozen.also { freezes-- }
            else -> DayMark.Missed.also { length = 0 }
        }
        day = day.plus(1, DateTimeUnit.DAY)
    }
    return PlayRun(
        length = length,
        freezes = freezes,
        freezeProgress = progress,
        week = weekMarks(today, marks),
        todayCleared = marks[today] == DayMark.Cleared,
    )
}

/** Before the first day of play a day is a plain blank; after today it is still to come. */
private fun weekMarks(today: LocalDate, marks: Map<LocalDate, DayMark>): List<DayMark> {
    val monday = today.mondayOfWeek()
    return (0 until DAYS_IN_WEEK).map { offset ->
        val day = monday.plus(offset, DateTimeUnit.DAY)
        when {
            day > today -> DayMark.Ahead
            day == today -> if (marks[day] == DayMark.Cleared) DayMark.Cleared else DayMark.Today
            else -> marks[day] ?: DayMark.Missed
        }
    }
}
