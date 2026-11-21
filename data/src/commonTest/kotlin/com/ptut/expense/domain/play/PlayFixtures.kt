package com.ptut.expense.domain.play

import com.ptut.expense.domain.BangkokTime
import com.ptut.expense.domain.Direction
import com.ptut.expense.domain.Satang
import com.ptut.expense.domain.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant

/** A row at noon on [day] in Bangkok, the zone every play test counts in. */
internal fun row(
    id: String,
    day: LocalDate,
    minor: Long,
    category: String? = "food",
    shop: String? = null,
    direction: Direction = Direction.Expense,
) = Transaction(
    id = id,
    occurredAtEpochMillis = LocalDateTime(day.year, day.month, day.day, 12, 0).toInstant(BangkokTime).toEpochMilliseconds(),
    amount = Satang(minor),
    direction = direction,
    categoryId = category,
    counterparty = shop,
)
