package com.ptut.expense.play

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant

/** Noon on [day] in Bangkok, held still. */
internal fun clockAt(day: LocalDate): Clock = object : Clock {
    override fun now(): Instant = LocalDateTime(day.year, day.month, day.day, 12, 0).toInstant(BangkokTime)
}

internal fun spendOn(id: String, day: LocalDate, minor: Long, category: String = "food", shop: String? = null, direction: Direction = Direction.Expense) = Transaction(
    id = id,
    occurredAtEpochMillis = LocalDateTime(day.year, day.month, day.day, 12, 0).toInstant(BangkokTime).toEpochMilliseconds(),
    amount = Satang(minor),
    direction = direction,
    categoryId = category,
    counterparty = shop,
)
