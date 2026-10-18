package com.ptut.expense.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Rows that entered the ledger on [day] but carry another day's date, newest save first.
 *
 * A row dated [day] is left out because Today's list already draws it, and one row is drawn once.
 * A row with no [Transaction.filedAt] was written before schema 9, and nothing is guessed for it.
 */
fun List<Transaction>.savedOn(day: LocalDate, zone: TimeZone = BangkokTime): List<Transaction> =
    filter { row ->
        val filedAt = row.filedAt ?: return@filter false
        filedAt.dateIn(zone) == day && row.occurredAtEpochMillis.dateIn(zone) != day
    }.sortedByDescending { it.filedAt }
