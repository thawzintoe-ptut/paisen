package com.ptut.expense.domain

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Sky's moods by the clock alone (p07, p08): morning to noon, evening from 18:00, the day between. */
enum class DayPart { Morning, Day, Evening }

/** On Bangkok's clock (FR-19). Evening runs to midnight, where the next morning starts (#121, #122). */
fun dayPartAt(now: Instant, zone: TimeZone = BangkokTime): DayPart {
    val hour = now.toLocalDateTime(zone).hour
    return when {
        hour < NOON -> DayPart.Morning
        hour >= EVENING -> DayPart.Evening
        else -> DayPart.Day
    }
}

private const val NOON = 12
private const val EVENING = 18
