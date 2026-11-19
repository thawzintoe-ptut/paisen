package com.ptut.expense.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Rows that entered the ledger on [day] but carry another day's date, newest save first.
 *
 * A row dated [day] is left out because Today's list already draws it, and one row is drawn once.
 * A row with no [Transaction.filedAt] was written before schema 9, and nothing is guessed for it.
 */
fun List<Transaction>.savedOn(day: LocalDate, zone: TimeZone = HomeTime): List<Transaction> =
    filter { row ->
        val filedAt = row.filedAt ?: return@filter false
        filedAt.dateIn(zone) == day && row.occurredAtEpochMillis.dateIn(zone) != day
    }.sortedByDescending { it.filedAt }

/** What went out on [day], on [zone] days. Income never counts. */
fun List<Transaction>.spentOn(day: LocalDate, zone: TimeZone = HomeTime): Satang =
    filter { it.direction == Direction.Expense && it.occurredAtEpochMillis.dateIn(zone) == day }.map { it.amount }.sum()
